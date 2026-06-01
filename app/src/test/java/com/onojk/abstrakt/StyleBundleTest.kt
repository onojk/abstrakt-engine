package com.onojk.abstrakt

import org.junit.Assert.*
import org.junit.Test

class StyleBundleTest {

    @Test
    fun `all bundles are present in ALL list`() {
        assertEquals(7, StyleBundle.ALL.size)
        assertTrue(StyleBundle.DEFAULT    in StyleBundle.ALL)
        assertTrue(StyleBundle.CALM       in StyleBundle.ALL)
        assertTrue(StyleBundle.AGGRESSIVE in StyleBundle.ALL)
        assertTrue(StyleBundle.COSMIC     in StyleBundle.ALL)
        assertTrue(StyleBundle.VINTAGE    in StyleBundle.ALL)
        assertTrue(StyleBundle.MINIMAL    in StyleBundle.ALL)
        assertTrue(StyleBundle.PSYCHEDELIC in StyleBundle.ALL)
    }

    @Test
    fun `Default is first in ALL`() {
        assertEquals(StyleBundle.DEFAULT, StyleBundle.ALL.first())
    }

    @Test
    fun `all bundle names are non-empty and unique`() {
        val names = StyleBundle.ALL.map { it.name }
        assertTrue(names.all { it.isNotBlank() })
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `all foldCounts are in valid range 2 to 24`() {
        StyleBundle.ALL.forEach { b ->
            assertTrue("${b.name} foldCount=${b.foldCount}", b.foldCount in 2..24)
        }
    }

    @Test
    fun `all colorizeHue values are in 0 to 360`() {
        StyleBundle.ALL.forEach { b ->
            assertTrue("${b.name} colorizeHue=${b.colorizeHue}", b.colorizeHue in 0f..360f)
        }
    }

    @Test
    fun `all zoomMultiplier values are in 0_5 to 1_5`() {
        StyleBundle.ALL.forEach { b ->
            assertTrue("${b.name} zoom=${b.zoomMultiplier}", b.zoomMultiplier in 0.5f..1.5f)
        }
    }

    @Test
    fun `all distortionAmplitude values are in 0 to 1`() {
        StyleBundle.ALL.forEach { b ->
            assertTrue("${b.name} amp=${b.distortionAmplitude}", b.distortionAmplitude in 0f..1f)
        }
    }

    @Test
    fun `all distortionFrequency values are in 0_5 to 8_0`() {
        StyleBundle.ALL.forEach { b ->
            assertTrue("${b.name} freq=${b.distortionFrequency}", b.distortionFrequency in 0.5f..8.0f)
        }
    }

    @Test
    fun `all distortionPlus angles are in valid ranges`() {
        StyleBundle.ALL.forEach { b ->
            assertTrue("${b.name} yaw=${b.distortionPlusYaw}", b.distortionPlusYaw in -180f..180f)
            assertTrue("${b.name} pitch=${b.distortionPlusPitch}", b.distortionPlusPitch in -90f..90f)
            assertTrue("${b.name} roll=${b.distortionPlusRoll}", b.distortionPlusRoll in -180f..180f)
        }
    }

    @Test
    fun `all bassZoomIntensity values are in 0 to 1`() {
        StyleBundle.ALL.forEach { b ->
            assertTrue("${b.name} bassZoom=${b.bassZoomIntensity}", b.bassZoomIntensity in 0f..1f)
        }
    }

    @Test
    fun `all contrast values are in 0 to 2`() {
        StyleBundle.ALL.forEach { b ->
            assertTrue("${b.name} contrast=${b.contrast}", b.contrast in 0f..2f)
        }
    }

    @Test
    fun `all contrastPasses values are in 1 to 6`() {
        StyleBundle.ALL.forEach { b ->
            assertTrue("${b.name} passes=${b.contrastPasses}", b.contrastPasses in 1..6)
        }
    }

    @Test
    fun `all saturation values are in 0 to 2`() {
        StyleBundle.ALL.forEach { b ->
            assertTrue("${b.name} sat=${b.saturation}", b.saturation in 0f..2f)
        }
    }

    @Test
    fun `all beatReactivity values are in 0 to 1`() {
        StyleBundle.ALL.forEach { b ->
            assertTrue("${b.name} reactivity=${b.beatReactivity}", b.beatReactivity in 0f..1f)
        }
    }

    @Test
    fun `no float fields are NaN or Inf`() {
        StyleBundle.ALL.forEach { b ->
            listOf(
                b.colorizeHue, b.zoomMultiplier, b.distortionAmplitude, b.distortionFrequency,
                b.distortionPlusYaw, b.distortionPlusPitch, b.distortionPlusRoll,
                b.bassZoomIntensity, b.contrast, b.saturation, b.beatReactivity,
            ).forEachIndexed { i, v ->
                assertFalse("${b.name} field[$i] is NaN", v.isNaN())
                assertFalse("${b.name} field[$i] is Inf", v.isInfinite())
            }
        }
    }

    @Test
    fun `Default bundle has neutral baseline values`() {
        with(StyleBundle.DEFAULT) {
            assertEquals(6, foldCount)
            assertEquals(ShapeKind.Cube, shapeKind)
            assertEquals(FrameShape.Circle, frameShape)
            assertFalse(invertColors)
            assertFalse(colorizeEnabled)
            assertFalse(distortionEnabled)
            assertFalse(distortionPlusEnabled)
            assertEquals(1.0f, contrast, 1e-5f)
            assertEquals(1, contrastPasses)
            assertEquals(1.0f, saturation, 1e-5f)
        }
    }
}
