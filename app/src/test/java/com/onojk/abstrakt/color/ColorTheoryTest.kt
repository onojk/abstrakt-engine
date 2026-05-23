package com.onojk.abstrakt.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

private const val EPS = 1e-5f

private fun assertNear(expected: Float, actual: Float, eps: Float = EPS, msg: String = "") =
    assertTrue("$msg expected ~$expected but was $actual", kotlin.math.abs(actual - expected) <= eps)

private fun assertRgbNear(expected: FloatArray, actual: FloatArray, eps: Float = EPS) {
    assertNear(expected[0], actual[0], eps, "R")
    assertNear(expected[1], actual[1], eps, "G")
    assertNear(expected[2], actual[2], eps, "B")
}

class ColorTheoryTest {

    // ── wrapHue ───────────────────────────────────────────────────────────────

    @Test fun `wrapHue - zero stays zero`()        = assertNear(0f,   wrapHue(0f))
    @Test fun `wrapHue - 360 wraps to zero`()      = assertNear(0f,   wrapHue(360f))
    @Test fun `wrapHue - 361 wraps to 1`()         = assertNear(1f,   wrapHue(361f))
    @Test fun `wrapHue - negative -30 to 330`()    = assertNear(330f, wrapHue(-30f))
    @Test fun `wrapHue - negative -720 to 0`()     = assertNear(0f,   wrapHue(-720f))
    @Test fun `wrapHue - 720_5 to 0_5`()           = assertNear(0.5f, wrapHue(720.5f))

    // ── hsvToRgb primaries ────────────────────────────────────────────────────

    @Test fun `hsvToRgb - red`()     = assertRgbNear(floatArrayOf(1f, 0f, 0f), hsvToRgb(0f,   1f, 1f))
    @Test fun `hsvToRgb - green`()   = assertRgbNear(floatArrayOf(0f, 1f, 0f), hsvToRgb(120f, 1f, 1f))
    @Test fun `hsvToRgb - blue`()    = assertRgbNear(floatArrayOf(0f, 0f, 1f), hsvToRgb(240f, 1f, 1f))
    @Test fun `hsvToRgb - yellow`()  = assertRgbNear(floatArrayOf(1f, 1f, 0f), hsvToRgb(60f,  1f, 1f))
    @Test fun `hsvToRgb - cyan`()    = assertRgbNear(floatArrayOf(0f, 1f, 1f), hsvToRgb(180f, 1f, 1f))
    @Test fun `hsvToRgb - magenta`() = assertRgbNear(floatArrayOf(1f, 0f, 1f), hsvToRgb(300f, 1f, 1f))

    @Test fun `hsvToRgb - black (v=0)`() =
        assertRgbNear(floatArrayOf(0f, 0f, 0f), hsvToRgb(180f, 1f, 0f))

    @Test fun `hsvToRgb - white (s=0)`() =
        assertRgbNear(floatArrayOf(1f, 1f, 1f), hsvToRgb(0f, 0f, 1f))

    @Test fun `hsvToRgb - hue wraps 360 equals 0`() =
        assertRgbNear(hsvToRgb(0f, 0.8f, 0.7f), hsvToRgb(360f, 0.8f, 0.7f))

    // ── rgbToHsv round-trip ───────────────────────────────────────────────────

    private fun roundTrip(h: Float, s: Float, v: Float) {
        val rgb = hsvToRgb(h, s, v)
        val (hOut, sOut, vOut) = rgbToHsv(rgb)
        assertNear(wrapHue(h), hOut, 1e-4f, "hue round-trip h=$h")
        assertNear(s,          sOut, 1e-4f, "sat round-trip s=$s")
        assertNear(v,          vOut, 1e-4f, "val round-trip v=$v")
    }

    @Test fun `rgbToHsv round-trip red`()        = roundTrip(0f,   1f,   1f)
    @Test fun `rgbToHsv round-trip green`()       = roundTrip(120f, 1f,   1f)
    @Test fun `rgbToHsv round-trip blue`()        = roundTrip(240f, 1f,   1f)
    @Test fun `rgbToHsv round-trip arbitrary 1`() = roundTrip(37f,  0.6f, 0.8f)
    @Test fun `rgbToHsv round-trip arbitrary 2`() = roundTrip(275f, 0.9f, 0.5f)

    @Test fun `rgbToHsv - pure gray hue is 0 by convention`() {
        val (h, s, v) = rgbToHsv(floatArrayOf(0.5f, 0.5f, 0.5f))
        assertNear(0f,   h)
        assertNear(0f,   s)
        assertNear(0.5f, v)
    }

    // ── ColorHarmony.hueOffsets ───────────────────────────────────────────────

    @Test fun `hueOffsets - Monochromatic`() =
        assertTrue(ColorHarmony.Monochromatic.hueOffsets().contentEquals(intArrayOf(0)))

    @Test fun `hueOffsets - Analogous`() =
        assertTrue(ColorHarmony.Analogous.hueOffsets().contentEquals(intArrayOf(0, -30, 30)))

    @Test fun `hueOffsets - Complementary`() =
        assertTrue(ColorHarmony.Complementary.hueOffsets().contentEquals(intArrayOf(0, 180)))

    @Test fun `hueOffsets - SplitComplementary`() =
        assertTrue(ColorHarmony.SplitComplementary.hueOffsets().contentEquals(intArrayOf(0, 150, 210)))

    @Test fun `hueOffsets - Triadic`() =
        assertTrue(ColorHarmony.Triadic.hueOffsets().contentEquals(intArrayOf(0, 120, 240)))

    @Test fun `hueOffsets - Tetradic`() =
        assertTrue(ColorHarmony.Tetradic.hueOffsets().contentEquals(intArrayOf(0, 90, 180, 270)))

    // ── next() cycles ─────────────────────────────────────────────────────────

    @Test fun `ColorHarmony next cycles correctly`() {
        assertEquals(ColorHarmony.Analogous,          ColorHarmony.Monochromatic.next())
        assertEquals(ColorHarmony.Complementary,      ColorHarmony.Analogous.next())
        assertEquals(ColorHarmony.SplitComplementary, ColorHarmony.Complementary.next())
        assertEquals(ColorHarmony.Triadic,            ColorHarmony.SplitComplementary.next())
        assertEquals(ColorHarmony.Tetradic,           ColorHarmony.Triadic.next())
        assertEquals(ColorHarmony.Monochromatic,      ColorHarmony.Tetradic.next())
    }

    @Test fun `SaturationMode next cycles correctly`() {
        assertEquals(SaturationMode.Pure,          SaturationMode.Free.next())
        assertEquals(SaturationMode.Muted,         SaturationMode.Pure.next())
        assertEquals(SaturationMode.ChromaticGray, SaturationMode.Muted.next())
        assertEquals(SaturationMode.Free,          SaturationMode.ChromaticGray.next())
    }

    @Test fun `ValueKey next cycles correctly`() {
        assertEquals(ValueKey.High, ValueKey.Free.next())
        assertEquals(ValueKey.Mid,  ValueKey.High.next())
        assertEquals(ValueKey.Low,  ValueKey.Mid.next())
        assertEquals(ValueKey.Free, ValueKey.Low.next())
    }

    // ── SaturationMode range / defaultValue / clamp ───────────────────────────

    @Test fun `SaturationMode ranges are correct`() {
        assertEquals(0f    to 1f,    SaturationMode.Free.range())
        assertEquals(0.75f to 1f,    SaturationMode.Pure.range())
        assertEquals(0.30f to 0.65f, SaturationMode.Muted.range())
        assertEquals(0.05f to 0.25f, SaturationMode.ChromaticGray.range())
    }

    @Test fun `SaturationMode defaultValue is midpoint`() {
        assertNear(0.5f,    SaturationMode.Free.defaultValue())
        assertNear(0.875f,  SaturationMode.Pure.defaultValue())
        assertNear(0.475f,  SaturationMode.Muted.defaultValue())
        assertNear(0.15f,   SaturationMode.ChromaticGray.defaultValue())
    }

    @Test fun `SaturationMode clamp respects floor`() =
        assertNear(0.75f, SaturationMode.Pure.clamp(0.5f))

    @Test fun `SaturationMode clamp respects ceiling`() =
        assertNear(1.0f, SaturationMode.Pure.clamp(1.2f))

    @Test fun `SaturationMode clamp passes through in-range value`() =
        assertNear(0.9f, SaturationMode.Pure.clamp(0.9f))

    @Test fun `SaturationMode ChromaticGray clamp`() {
        assertNear(0.05f, SaturationMode.ChromaticGray.clamp(0.0f))
        assertNear(0.25f, SaturationMode.ChromaticGray.clamp(0.9f))
        assertNear(0.15f, SaturationMode.ChromaticGray.clamp(0.15f))
    }

    // ── ValueKey range / defaultValue / clamp ─────────────────────────────────

    @Test fun `ValueKey ranges are correct`() {
        assertEquals(0f    to 1f,    ValueKey.Free.range())
        assertEquals(0.75f to 1f,    ValueKey.High.range())
        assertEquals(0.40f to 0.75f, ValueKey.Mid.range())
        assertEquals(0.15f to 0.40f, ValueKey.Low.range())
    }

    @Test fun `ValueKey defaultValue is midpoint`() {
        assertNear(0.5f,   ValueKey.Free.defaultValue())
        assertNear(0.875f, ValueKey.High.defaultValue())
        assertNear(0.575f, ValueKey.Mid.defaultValue())
        assertNear(0.275f, ValueKey.Low.defaultValue())
    }

    @Test fun `ValueKey clamp Low respects floor`() =
        assertNear(0.15f, ValueKey.Low.clamp(0.05f))

    @Test fun `ValueKey clamp Low respects ceiling`() =
        assertNear(0.40f, ValueKey.Low.clamp(0.80f))

    @Test fun `ValueKey clamp passes through in-range value`() =
        assertNear(0.60f, ValueKey.Mid.clamp(0.60f))

    // ── paletteFromHarmony ────────────────────────────────────────────────────

    @Test fun `paletteFromHarmony returns exactly count colors`() {
        for (harmony in ColorHarmony.values()) {
            for (count in listOf(1, 3, 6, 10)) {
                val palette = paletteFromHarmony(harmony, 0f, 0.8f, 0.8f, count)
                assertEquals("$harmony count=$count", count, palette.size)
                palette.forEach { assertEquals(3, it.size) }
            }
        }
    }

    @Test fun `paletteFromHarmony - tetradic 6 colors has no exact duplicates`() {
        val palette = paletteFromHarmony(ColorHarmony.Tetradic, 45f, 0.8f, 0.9f, 6)
        assertEquals(6, palette.size)
        // per-cycle modulation shifts v and s each cycle, so cycle 0 (i=0..3) and cycle 1
        // (i=4..5) cannot produce identical colors even for matching hue offsets
        for (i in 0 until palette.size) {
            for (j in i + 1 until palette.size) {
                val a = palette[i]; val b = palette[j]
                val same = (0 until 3).all { abs(a[it] - b[it]) < EPS }
                assertFalse("palette[$i] == palette[$j]", same)
            }
        }
    }

    @Test fun `paletteFromHarmony - per-cycle value decreases`() {
        // For count > offsets.size, cycle 1 colors should have lower value than cycle 0
        // (value - 0.12 per cycle, clamped at 0.15)
        val harmony   = ColorHarmony.Complementary   // 2 offsets
        val baseValue = 0.9f
        val palette   = paletteFromHarmony(harmony, 0f, 0.7f, baseValue, 4)
        // i=0,1 → cycle 0 → v = 0.9; i=2,3 → cycle 1 → v = 0.78
        val rgb0 = palette[0]; val rgb2 = palette[2]
        val (_, _, v0) = rgbToHsv(rgb0)
        val (_, _, v2) = rgbToHsv(rgb2)
        assertTrue("cycle-1 value should be lower: $v2 < $v0", v2 < v0 - 0.05f)
    }

    @Test fun `paletteFromHarmony - anchor hue is honored`() {
        val anchorHue = 200f
        val palette   = paletteFromHarmony(ColorHarmony.Monochromatic, anchorHue, 0.8f, 0.8f, 1)
        val (h, _, _) = rgbToHsv(palette[0])
        assertNear(anchorHue, h, 1e-3f)
    }

    // ── applyTemperatureBias ──────────────────────────────────────────────────

    @Test fun `applyTemperatureBias - zero bias is no-op`() {
        for (hue in listOf(0f, 90f, 180f, 270f, 359f)) {
            assertNear(hue, applyTemperatureBias(hue, 0f), msg = "hue=$hue")
        }
    }

    @Test fun `applyTemperatureBias - positive bias pulls toward 30 degrees`() {
        // hue=90 is 60° away from warm pole 30; bias=0.5 pulls by 30° → expect ~60°
        val result = applyTemperatureBias(90f, 0.5f)
        assertNear(60f, result, 1e-3f)
    }

    @Test fun `applyTemperatureBias - negative bias pulls toward 210 degrees`() {
        // hue=90 is 120° away from cool pole 210; delta clamped to 60; bias=-0.5 pulls by 30° → 120°
        val result = applyTemperatureBias(90f, -0.5f)
        assertNear(120f, result, 1e-3f)
    }

    @Test fun `applyTemperatureBias - max bias 1_0 caps at 60 degree pull`() {
        // hue=300 → shortest arc to 30 is +90°, clamped to 60, bias=1 → pull=60 → 360=0
        val result = applyTemperatureBias(300f, 1.0f)
        assertNear(0f, result, 1e-3f)
    }

    @Test fun `applyTemperatureBias - result always in 0-360`() {
        val biases = listOf(-1f, -0.5f, 0.1f, 0.5f, 1f)
        val hues   = listOf(0f, 1f, 29f, 30f, 31f, 90f, 180f, 210f, 300f, 359f)
        for (h in hues) for (b in biases) {
            val r = applyTemperatureBias(h, b)
            assertTrue("result $r out of [0,360) for h=$h b=$b", r >= 0f && r < 360f)
        }
    }

    // ── randomHsv ─────────────────────────────────────────────────────────────

    @Test fun `randomHsv - seeded Random is deterministic`() {
        val a = randomHsv(Random(42), floatArrayOf(0f, 360f), floatArrayOf(0.4f, 1f), floatArrayOf(0.5f, 1f))
        val b = randomHsv(Random(42), floatArrayOf(0f, 360f), floatArrayOf(0.4f, 1f), floatArrayOf(0.5f, 1f))
        assertRgbNear(a, b)
    }

    @Test fun `randomHsv - result stays within provided ranges`() {
        val hueRange = floatArrayOf(60f,  180f)
        val satRange = floatArrayOf(0.3f, 0.7f)
        val valRange = floatArrayOf(0.5f, 0.9f)
        val rng = Random(99)
        repeat(200) {
            val rgb = randomHsv(rng, hueRange, satRange, valRange)
            val (h, s, v) = rgbToHsv(rgb)
            assertTrue("hue $h not in [60,180]",    h in 60f..180.001f)
            assertTrue("sat $s not in [0.3,0.7]",   s in 0.3f..0.701f)
            assertTrue("val $v not in [0.5,0.9]",   v in 0.5f..0.901f)
        }
    }

    // ── randomColorTasteful ───────────────────────────────────────────────────

    @Test fun `randomColorTasteful - stays within tasteful ranges`() {
        val rng = Random(7)
        repeat(200) {
            val rgb = randomColorTasteful(rng)
            val (_, s, v) = rgbToHsv(rgb)
            assertTrue("sat $s not in [0.55,0.95]", s in 0.549f..0.951f)
            assertTrue("val $v not in [0.65,1.0]",  v in 0.649f..1.001f)
        }
    }

    // ── No Android / renderer imports ─────────────────────────────────────────
    // (Enforced at compile time: this test runs on the JVM without Android runtime.)
}
