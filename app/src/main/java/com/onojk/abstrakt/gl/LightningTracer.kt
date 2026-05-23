package com.onojk.abstrakt.gl

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal data class BoltSegment(
    val x0: Float, val y0: Float,
    val x1: Float, val y1: Float,
    val weight: Float,
)

internal object LightningTracer {
    // Traces a short jagged branch from (sx,sy) at startAngle.
    // weight = local trunk thickness at the branch point (branch tapers thinner).
    fun traceBranch(
        sx: Float, sy: Float,
        startAngle: Float,
        rng: Random,
        weight: Float,
    ): List<BoltSegment> {
        val out = mutableListOf<BoltSegment>()
        var angle = startAngle
        var x = sx; var y = sy
        val steps = rng.nextInt(3, 6)
        for (i in 0 until steps) {
            angle += if (rng.nextFloat() < 0.35f)
                (rng.nextFloat() - 0.5f) * 2.0f
            else
                (rng.nextFloat() - 0.5f) * 0.28f
            val nx = x + cos(angle) * 0.028f
            val ny = y + sin(angle) * 0.028f
            val w  = (weight * (1.0f - i.toFloat() / steps * 0.55f)).coerceAtLeast(0.05f)
            out.add(BoltSegment(x, y, nx, ny, w))
            x = nx; y = ny
        }
        return out
    }
}
