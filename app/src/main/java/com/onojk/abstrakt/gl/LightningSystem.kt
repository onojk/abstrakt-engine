package com.onojk.abstrakt.gl

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val CREATURE_COUNT  = 8
private const val BODY_LENGTH     = 32   // trail points → 31 wedge segments = long kite tail
private const val TOTAL_SEGS      = 256  // matches shader MAX_SEGS

private const val STEP_SIZE       = 0.028f
private const val BURST_SPEED     = 55f
private const val TAIL_WEIGHT_MIN = 0.04f  // very thin at tail tip so it dissolves to nothing
private const val CREATURE_LIFE   = 16f
private const val FADE_DURATION   = 1.5f

private val TAU = (2.0 * PI).toFloat()

internal class LightningSystem {

    // ── Drifting concentration zones ─────────────────────────────────────────
    private class Zone(
        val cx: Float, val cy: Float,
        val driftSpeed: Float, val driftRadius: Float,
        val phaseX: Float, val phaseY: Float,
    ) {
        fun posAt(t: Float): Pair<Float, Float> =
            cx + sin(t * driftSpeed + phaseX) * driftRadius to
            cy + cos(t * driftSpeed * 0.7f + phaseY) * driftRadius
    }

    private val zones = arrayOf(
        Zone(0.28f, 0.50f, 0.09f, 0.22f, 0.00f, 1.40f),
        Zone(0.72f, 0.50f, 0.12f, 0.25f, 2.10f, 0.70f),
        Zone(0.50f, 0.25f, 0.07f, 0.20f, 4.30f, 3.10f),
    )

    private class TrailPoint(val x: Float, val y: Float, val noise: Float)

    // ── Living crawling creature ──────────────────────────────────────────────
    private class Creature(
        var headX: Float, var headY: Float,
        var angle: Float,
        val jitterSeed: Float,
        var thickPhase: Float,
        val thickSpeed: Float,
        var targetZoneIdx: Int,
        val spawnTime: Float,
        val lifetime: Float,
    ) {
        val trail = ArrayDeque<TrailPoint>()

        var inBrace        = false
        var braceTimer     = 0f
        var burstStepsLeft = 0
        var burstAccum     = 0f
        var nextBurstSteps = 0
        var nextBraceDur   = 0f
    }

    private val creatures = ArrayList<Creature>(CREATURE_COUNT)
    private var lastTime  = 0f
    private val rng       = Random(System.nanoTime())

    // ── GPU-ready buffers (pre-allocated) ────────────────────────────────────
    // segA = head-side (base, wide end of wedge)
    // segB = tail-side (tip, narrow end of wedge)
    val segA     = FloatArray(TOTAL_SEGS * 2)
    val segB     = FloatArray(TOTAL_SEGS * 2)
    val segW     = FloatArray(TOTAL_SEGS)
    var segCount = 0

    fun reset() {
        creatures.clear()
        lastTime = 0f
        segCount = 0
    }

    fun update(timeSec: Float, touchX: Float, touchY: Float, touching: Boolean) {
        val dt = if (lastTime > 0f) (timeSec - lastTime).coerceIn(0f, 0.05f) else 0f
        lastTime = timeSec

        while (creatures.size < CREATURE_COUNT) {
            spawnCreature(timeSec, zones[rng.nextInt(zones.size)].posAt(timeSec))
        }

        if (touching) {
            creatures.minByOrNull { c ->
                val dx = c.headX - touchX; val dy = c.headY - touchY
                dx * dx + dy * dy
            }?.let { c ->
                val ta   = atan2(touchY - c.headY, touchX - c.headX)
                val diff = normalizeAngle(ta - c.angle)
                c.angle += diff * 0.35f
            }
        }

        segCount = 0
        val dead = ArrayList<Creature>()

        for (c in creatures) {
            val age = timeSec - c.spawnTime
            if (age >= c.lifetime) { dead.add(c); continue }

            val bri = creatureBri(age, c.lifetime)
            if (bri < 0.005f) continue

            c.thickPhase += c.thickSpeed * dt

            if (rng.nextFloat() < 0.004f) c.targetZoneIdx = rng.nextInt(zones.size)

            advanceGait(c, timeSec, dt)

            // ── Pack kite-tail wedges: head→segA (wide base), tail→segB (tip) ──
            val n = c.trail.size
            if (n >= 2) {
                val headThick = 0.55f + 0.45f * sin(c.thickPhase)
                val nSeg = n - 1
                for (i in 0 until nSeg) {
                    if (segCount >= TOTAL_SEGS) break
                    val p0 = c.trail[i]      // older = closer to tail = wedge tip
                    val p1 = c.trail[i + 1]  // newer = closer to head = wedge base
                    // i = 0 → tail (thin), i = nSeg-1 → head (fat)
                    val taper = TAIL_WEIGHT_MIN +
                        (1f - TAIL_WEIGHT_MIN) * (i.toFloat() / nSeg.coerceAtLeast(1))
                    val w = (taper * headThick * p1.noise * bri).coerceIn(0.01f, 1.0f)
                    // Pass head-side (p1) first → segA (base), tail-side (p0) second → segB (tip)
                    packSeg(p1.x, p1.y, p0.x, p0.y, timeSec, c.jitterSeed, w)
                }
            }
        }

        creatures.removeAll(dead)
    }

    private fun advanceGait(c: Creature, timeSec: Float, dt: Float) {
        if (c.inBrace) {
            c.braceTimer -= dt
            if (c.braceTimer <= 0f) {
                c.inBrace        = false
                c.burstAccum     = 0f
                c.burstStepsLeft = c.nextBurstSteps
            }
        } else {
            c.burstAccum += BURST_SPEED * dt
            while (c.burstAccum >= 1f && c.burstStepsLeft > 0) {
                c.burstAccum -= 1f
                c.burstStepsLeft--
                step(c, timeSec)
            }
            if (c.burstStepsLeft <= 0) {
                c.inBrace    = true
                c.braceTimer = c.nextBraceDur
                planNextGait(c)
            }
        }
    }

    private fun step(c: Creature, timeSec: Float) {
        c.angle += if (rng.nextFloat() < 0.28f)
            (rng.nextFloat() - 0.5f) * 2.0f
        else
            (rng.nextFloat() - 0.5f) * 0.22f

        val (zx, zy) = zones[c.targetZoneIdx].posAt(timeSec)
        val ta   = atan2(zy - c.headY, zx - c.headX)
        c.angle += normalizeAngle(ta - c.angle) * 0.07f

        val bx = if (c.headX < 0.12f) 1f else if (c.headX > 0.88f) -1f else 0f
        val by = if (c.headY < 0.08f) 1f else if (c.headY > 0.92f) -1f else 0f
        if (bx != 0f || by != 0f) {
            c.angle += normalizeAngle(atan2(by, bx) - c.angle) * 0.28f
        }

        val nx = c.headX + cos(c.angle) * STEP_SIZE
        val ny = c.headY + sin(c.angle) * STEP_SIZE
        c.headX = nx; c.headY = ny

        c.trail.addLast(TrailPoint(nx, ny, 0.78f + rng.nextFloat() * 0.44f))
        while (c.trail.size > BODY_LENGTH) c.trail.removeFirst()
    }

    private fun planNextGait(c: Creature) {
        c.nextBurstSteps = 4 + rng.nextInt(7)
        c.nextBraceDur   = 0.06f + rng.nextFloat() * 0.24f
    }

    private fun creatureBri(age: Float, lifetime: Float): Float =
        if (age < lifetime - FADE_DURATION) 1.0f
        else ((lifetime - age) / FADE_DURATION).coerceIn(0f, 1f)

    // segA receives x0/y0 (head-side base), segB receives x1/y1 (tail-side tip).
    // Crawl jitter uses a spatial+time function; both vertices of a shared trail point
    // get identical displacement → adjacent wedges stay connected.
    private fun packSeg(
        x0: Float, y0: Float, x1: Float, y1: Float,
        t: Float, seed: Float, w: Float,
    ) {
        if (segCount >= TOTAL_SEGS) return
        val ax = x0 + sin(t * 5.5f + x0 * 8.0f + seed) * 0.010f
        val ay = y0 + cos(t * 5.0f + y0 * 9.0f + seed + 1.3f) * 0.010f
        val bx = x1 + sin(t * 5.5f + x1 * 8.0f + seed) * 0.010f
        val by = y1 + cos(t * 5.0f + y1 * 9.0f + seed + 1.3f) * 0.010f
        val i2 = segCount * 2
        segA[i2] = ax; segA[i2 + 1] = ay
        segB[i2] = bx; segB[i2 + 1] = by
        segW[segCount] = w
        segCount++
    }

    private fun spawnCreature(timeSec: Float, pos: Pair<Float, Float>) {
        val lifetime = (CREATURE_LIFE + (rng.nextFloat() - 0.5f) * 8f).coerceAtLeast(8f)
        val c = Creature(
            headX         = pos.first,
            headY         = pos.second,
            angle         = rng.nextFloat() * TAU,
            jitterSeed    = rng.nextFloat() * 100f,
            thickPhase    = rng.nextFloat() * TAU,
            thickSpeed    = 0.30f + rng.nextFloat() * 0.55f,
            targetZoneIdx = rng.nextInt(zones.size),
            spawnTime     = timeSec,
            lifetime      = lifetime,
        )
        c.trail.addLast(TrailPoint(pos.first, pos.second, 1.0f))
        planNextGait(c)
        c.burstStepsLeft = c.nextBurstSteps
        creatures.add(c)
    }

    private fun normalizeAngle(a: Float): Float {
        var r = a % TAU
        if (r >  PI.toFloat()) r -= TAU
        if (r < -PI.toFloat()) r += TAU
        return r
    }
}
