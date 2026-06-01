package com.onojk.abstrakt

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class BpmRotationTest {

    // Mirrors the private bpmRotationTarget() logic for unit testing.
    private fun target(bpm: Float?, beatsPerRev: Int, lockOn: Boolean): Float? {
        if (!lockOn) return null
        bpm ?: return null
        return (2.0 * PI / beatsPerRev * (bpm / 60.0)).toFloat()
    }

    @Test
    fun `120 BPM at 8 beats per revolution gives pi over 2 rad per sec`() {
        // (2π / 8) × (120 / 60) = π/2 ≈ 1.5708 rad/s
        val result = target(bpm = 120f, beatsPerRev = 8, lockOn = true)
        assertNotNull(result)
        assertEquals((PI / 2).toFloat(), result!!, 1e-5f)
    }

    @Test
    fun `60 BPM at 4 beats per revolution gives pi over 2 rad per sec`() {
        // (2π / 4) × (60 / 60) = π/2 — same speed as 120 BPM at 8 beats/rev
        val result = target(bpm = 60f, beatsPerRev = 4, lockOn = true)
        assertNotNull(result)
        assertEquals((PI / 2).toFloat(), result!!, 1e-5f)
    }

    @Test
    fun `120 BPM at 1 beat per revolution gives 4pi rad per sec`() {
        // (2π / 1) × (120 / 60) = 4π — fastest setting
        val result = target(bpm = 120f, beatsPerRev = 1, lockOn = true)
        assertNotNull(result)
        assertEquals((4.0 * PI).toFloat(), result!!, 1e-5f)
    }

    @Test
    fun `120 BPM at 16 beats per revolution gives pi over 4 rad per sec`() {
        // (2π / 16) × (120 / 60) = π/4 — slowest setting
        val result = target(bpm = 120f, beatsPerRev = 16, lockOn = true)
        assertNotNull(result)
        assertEquals((PI / 4).toFloat(), result!!, 1e-5f)
    }

    @Test
    fun `null BPM returns null regardless of lock state`() {
        assertNull(target(bpm = null, beatsPerRev = 8, lockOn = true))
        assertNull(target(bpm = null, beatsPerRev = 8, lockOn = false))
    }

    @Test
    fun `lock off returns null even when BPM is available`() {
        assertNull(target(bpm = 120f, beatsPerRev = 8, lockOn = false))
    }

    @Test
    fun `triple meter divisor 3 gives correct speed`() {
        // (2π / 3) × (120 / 60) = 4π/3
        val result = target(bpm = 120f, beatsPerRev = 3, lockOn = true)
        assertNotNull(result)
        assertEquals((4.0 * PI / 3.0).toFloat(), result!!, 1e-5f)
    }
}
