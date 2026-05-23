package com.onojk.abstrakt.color

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// ── Enums ─────────────────────────────────────────────────────────────────────

enum class ColorHarmony {
    Monochromatic,
    Analogous,
    Complementary,
    SplitComplementary,
    Triadic,
    Tetradic;

    fun hueOffsets(): IntArray = when (this) {
        Monochromatic      -> intArrayOf(0)
        Analogous          -> intArrayOf(0, -30, 30)
        Complementary      -> intArrayOf(0, 180)
        SplitComplementary -> intArrayOf(0, 150, 210)
        Triadic            -> intArrayOf(0, 120, 240)
        Tetradic           -> intArrayOf(0, 90, 180, 270)
    }

    fun next(): ColorHarmony = values()[(ordinal + 1) % values().size]

    fun displayName(): String = when (this) {
        Monochromatic      -> "Monochromatic"
        Analogous          -> "Analogous"
        Complementary      -> "Complementary"
        SplitComplementary -> "Split Complementary"
        Triadic            -> "Triadic"
        Tetradic           -> "Tetradic"
    }
}

enum class SaturationMode {
    Free,
    Pure,
    Muted,
    ChromaticGray;

    fun range(): Pair<Float, Float> = when (this) {
        Free          -> 0f to 1f
        Pure          -> 0.75f to 1f
        Muted         -> 0.30f to 0.65f
        ChromaticGray -> 0.05f to 0.25f
    }

    fun defaultValue(): Float = range().let { (lo, hi) -> (lo + hi) / 2f }

    fun clamp(sat: Float): Float = range().let { (lo, hi) -> sat.coerceIn(lo, hi) }

    fun next(): SaturationMode = values()[(ordinal + 1) % values().size]
}

enum class ValueKey {
    Free,
    High,
    Mid,
    Low;

    fun range(): Pair<Float, Float> = when (this) {
        Free -> 0f to 1f
        High -> 0.75f to 1f
        Mid  -> 0.40f to 0.75f
        Low  -> 0.15f to 0.40f
    }

    fun defaultValue(): Float = range().let { (lo, hi) -> (lo + hi) / 2f }

    fun clamp(value: Float): Float = range().let { (lo, hi) -> value.coerceIn(lo, hi) }

    fun next(): ValueKey = values()[(ordinal + 1) % values().size]
}

// ── Pure functions ─────────────────────────────────────────────────────────────

fun wrapHue(deg: Float): Float = ((deg % 360f) + 360f) % 360f

fun hsvToRgb(hDeg: Float, s: Float, v: Float): FloatArray {
    val h  = wrapHue(hDeg)
    val sC = s.coerceIn(0f, 1f)
    val vC = v.coerceIn(0f, 1f)
    val c  = vC * sC
    val x  = c * (1f - abs((h / 60f) % 2f - 1f))
    val m  = vC - c
    val (r1, g1, b1) = when {
        h < 60f  -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else     -> Triple(c, 0f, x)
    }
    return floatArrayOf(r1 + m, g1 + m, b1 + m)
}

fun rgbToHsv(rgb: FloatArray): Triple<Float, Float, Float> {
    val r = rgb[0]; val g = rgb[1]; val b = rgb[2]
    val vMax  = max(r, max(g, b))
    val vMin  = min(r, min(g, b))
    val delta = vMax - vMin
    val v     = vMax
    val s     = if (vMax == 0f) 0f else delta / vMax
    if (s == 0f) return Triple(0f, 0f, v)
    val h = when {
        r == vMax -> 60f * ((g - b) / delta % 6f)
        g == vMax -> 60f * ((b - r) / delta + 2f)
        else      -> 60f * ((r - g) / delta + 4f)
    }
    return Triple(wrapHue(h), s, v)
}

fun paletteFromHarmony(
    harmony: ColorHarmony,
    anchorHue: Float,
    saturation: Float,
    value: Float,
    count: Int,
): List<FloatArray> {
    val offsets = harmony.hueOffsets()
    return List(count) { i ->
        val cycle  = i / offsets.size
        val hue    = wrapHue(anchorHue + offsets[i % offsets.size])
        val vCycle = (value      - cycle * 0.12f).coerceIn(0.15f, 1.0f)
        val sCycle = (saturation + cycle * 0.08f).coerceIn(0f,    1.0f)
        hsvToRgb(hue, sCycle, vCycle)
    }
}

fun randomHsv(
    rng: Random,
    hueRange: FloatArray,
    satRange: FloatArray,
    valRange: FloatArray,
): FloatArray {
    val h = hueRange[0] + rng.nextFloat() * (hueRange[1] - hueRange[0])
    val s = satRange[0]  + rng.nextFloat() * (satRange[1]  - satRange[0])
    val v = valRange[0]  + rng.nextFloat() * (valRange[1]  - valRange[0])
    return hsvToRgb(h, s, v)
}

fun randomColorTasteful(rng: Random): FloatArray = randomHsv(
    rng,
    hueRange = floatArrayOf(0f,    360f),
    satRange = floatArrayOf(0.55f, 0.95f),
    valRange = floatArrayOf(0.65f, 1.0f),
)

fun applyTemperatureBias(hueDeg: Float, bias: Float): Float {
    if (abs(bias) < 1e-6f) return hueDeg
    val target    = if (bias > 0f) 30f else 210f
    val deltaRaw  = target - hueDeg
    val delta     = ((deltaRaw + 180f) % 360f + 360f) % 360f - 180f
    val pull      = delta.coerceIn(-60f, 60f) * abs(bias)
    return wrapHue(hueDeg + pull)
}
