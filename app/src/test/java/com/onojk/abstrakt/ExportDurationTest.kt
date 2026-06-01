package com.onojk.abstrakt

import org.junit.Assert.*
import org.junit.Test

class ExportDurationTest {

    // ── formatDuration ────────────────────────────────────────────────────────

    @Test fun `formatDuration minimum`()  = assertEquals("0:05",  formatDuration(5))
    @Test fun `formatDuration thirty`()  = assertEquals("0:30",  formatDuration(30))
    @Test fun `formatDuration ninety`()  = assertEquals("1:30",  formatDuration(90))
    @Test fun `formatDuration five min`() = assertEquals("5:00", formatDuration(300))
    @Test fun `formatDuration ten min`() = assertEquals("10:00", formatDuration(600))

    // ── parseDurationInput ────────────────────────────────────────────────────

    @Test fun `parse mss 0_30`()        = assertEquals(30,  parseDurationInput("0:30"))
    @Test fun `parse mss 1_30`()        = assertEquals(90,  parseDurationInput("1:30"))
    @Test fun `parse mss 3_42`()        = assertEquals(222, parseDurationInput("3:42"))
    @Test fun `parse mss 10_00`()       = assertEquals(600, parseDurationInput("10:00"))
    @Test fun `parse bare 90`()         = assertEquals(90,  parseDurationInput("90"))
    @Test fun `parse bare 30`()         = assertEquals(30,  parseDurationInput("30"))
    @Test fun `parse garbage null`()    = assertNull(parseDurationInput("abc"))
    @Test fun `parse empty null`()      = assertNull(parseDurationInput(""))
    @Test fun `parse whitespace null`() = assertNull(parseDurationInput("   "))
    @Test fun `parse bad seconds null`() = assertNull(parseDurationInput("1:99"))
    @Test fun `parse too many colons null`() = assertNull(parseDurationInput("1:30:00"))

    // ── clampDuration ─────────────────────────────────────────────────────────

    @Test fun `clamp below min`()    = assertEquals(5,   clampDuration(2))
    @Test fun `clamp above max`()    = assertEquals(600, clampDuration(700))
    @Test fun `clamp within range`() = assertEquals(30,  clampDuration(30))
    @Test fun `clamp at min`()       = assertEquals(5,   clampDuration(5))
    @Test fun `clamp at max`()       = assertEquals(600, clampDuration(600))

    // ── round-trip: parse → clamp → format ───────────────────────────────────

    @Test fun `below-min input clamps to 0_05`() {
        val sec = parseDurationInput("0:02")!!
        assertEquals("0:05", formatDuration(clampDuration(sec)))
    }

    @Test fun `above-max input clamps to 10_00`() {
        val sec = parseDurationInput("12:00")!!
        assertEquals("10:00", formatDuration(clampDuration(sec)))
    }

    @Test fun `valid input round-trips cleanly`() {
        val sec = parseDurationInput("3:45")!!
        assertEquals("3:45", formatDuration(clampDuration(sec)))
    }
}
