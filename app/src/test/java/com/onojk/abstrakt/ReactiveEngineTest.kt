package com.onojk.abstrakt

import com.onojk.abstrakt.audio.AudioSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactiveEngineTest {

    private fun snap(low: Float, mid: Float = 0f): AudioSnapshot =
        AudioSnapshot(
            bands         = FloatArray(8),
            peak          = 0f,
            isBeat        = false,
            beatLow       = low,
            beatMid       = mid,
            beatHigh      = 0f,
            beatBroadband = 0f,
        )

    @Test
    fun `disabled engine never triggers`() {
        var fired = 0
        val e = ReactiveEngine { fired++ }
        e.enabled = false
        repeat(200) { e.onSnapshot(snap(1.0f), nowMs = it.toLong() * 16) }
        assertEquals(0, fired)
    }

    @Test
    fun `fires on a sustained peak after baseline warms up`() {
        var fired = 0
        val e = ReactiveEngine { fired++ }
        e.enabled = true
        e.intensity = 1.0f  // 1.05x threshold, 500ms cooldown
        repeat(300) { e.onSnapshot(snap(0.05f), nowMs = it.toLong() * 16) }
        repeat(20) { e.onSnapshot(snap(0.9f), nowMs = 300L * 16 + it.toLong() * 16) }
        assertTrue("expected at least one trigger, got $fired", fired >= 1)
    }

    @Test
    fun `cooldown prevents back-to-back fires`() {
        var fired = 0
        val e = ReactiveEngine { fired++ }
        e.enabled = true
        e.intensity = 0.0f  // 8000ms cooldown
        repeat(300) { e.onSnapshot(snap(0.05f), nowMs = it.toLong() * 16) }
        repeat(5) { e.onSnapshot(snap(0.9f), nowMs = 10_000L + it.toLong() * 16) }
        val firstCount = fired
        repeat(5) { e.onSnapshot(snap(0.9f), nowMs = 10_100L + it.toLong() * 16) }
        assertEquals("cooldown should suppress the second peak", firstCount, fired)
    }

    @Test
    fun `silence never triggers (baseline near zero gate)`() {
        var fired = 0
        val e = ReactiveEngine { fired++ }
        e.enabled = true
        e.intensity = 1.0f
        repeat(1000) { e.onSnapshot(snap(0.0f), nowMs = it.toLong() * 16) }
        assertEquals(0, fired)
    }
}
