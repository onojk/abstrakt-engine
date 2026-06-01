package com.onojk.abstrakt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PaletteTest {

    @Test
    fun `enum has expected ordinals — shader switch depends on these`() {
        assertEquals(0, PaletteMode.Off.ordinal)
        assertEquals(1, PaletteMode.Warm.ordinal)
        assertEquals(2, PaletteMode.Cool.ordinal)
        assertEquals(3, PaletteMode.Earth.ordinal)
        assertEquals(4, PaletteMode.Neon.ordinal)
        assertEquals(5, PaletteMode.Monochrome.ordinal)
    }

    @Test
    fun `all modes have non-empty labels`() {
        for (mode in PaletteMode.entries) {
            assertNotEquals("", mode.label)
        }
    }
}
