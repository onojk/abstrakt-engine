package com.onojk.abstrakt.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class NoiseFilterTest {

    // ── (a) HPF attenuates DC/low-freq content ────────────────────────────────

    @Test
    fun `HPF attenuates DC to near-zero after many samples`() {
        val filter = NoiseFilter(44100, true)
        // 4096 DC samples at full scale.
        // Float arithmetic stabilises at ~42 ULPs of 1.0 ≈ 5e-6; verify > 99.9% attenuation.
        val samples = FloatArray(4096) { 1.0f }
        filter.processSamples(samples)
        val lastAbs = abs(samples[4095])
        assertTrue("DC must be strongly attenuated by HPF (>99.9%), got $lastAbs", lastAbs < 0.001f)
    }

    @Test
    fun `HPF passes high-frequency content with little attenuation`() {
        val filter = NoiseFilter(44100, true)
        // 1000 Hz sine at amplitude 0.5 (above gate threshold to keep gate open)
        val sr = 44100
        val samples = FloatArray(4096) { i ->
            (0.5 * sin(2.0 * PI * 1000.0 * i / sr)).toFloat()
        }
        // Pre-warm filter to open gate (need rmsSmoothed > 0.020)
        val warmup = FloatArray(4096) { i ->
            (0.5 * sin(2.0 * PI * 1000.0 * i / sr)).toFloat()
        }
        filter.processSamples(warmup)

        val inputRms = sqrt(samples.map { it * it }.average()).toFloat()
        filter.processSamples(samples)
        val outputRms = sqrt(samples.map { it * it }.average()).toFloat()
        // 1000 Hz >> 80 Hz cutoff: minimal attenuation, output should be > 80% of input
        assertTrue("1000 Hz must pass through HPF; in=$inputRms out=$outputRms",
            outputRms > inputRms * 0.8f)
    }

    // ── (b) Sub-threshold RMS → gate closes, gain reduced to ~5% ─────────────

    @Test
    fun `gate attenuates sub-threshold signal to ~5 percent of input RMS`() {
        val filter = NoiseFilter(44100, true)
        // 440 Hz sine at tiny amplitude — RMS will be below GATE_CLOSE_THRESH (0.010)
        // Gate starts closed, so first call already applies GATE_CLOSED_GAIN = 0.05
        val sr = 44100
        val amplitude = 0.005f
        val samples = FloatArray(2048) { i ->
            (amplitude * sin(2.0 * PI * 440.0 * i / sr)).toFloat()
        }

        val inputRms = sqrt(samples.map { it * it }.average()).toFloat()
        filter.processSamples(samples)
        val outputRms = sqrt(samples.map { it * it }.average()).toFloat()

        // Gate should be closed (rmsSmoothed stays below 0.010), output ≈ input * 0.05
        // HPF attenuation at 440Hz is negligible; allow range 0.02..0.08
        val ratio = outputRms / inputRms
        assertTrue("Gated output ratio should be ≈0.05, got $ratio", ratio in 0.02f..0.08f)
    }

    // ── (c) processBands converges noise floor → output ~0 ───────────────────

    @Test
    fun `processBands floors converge and output approaches zero`() {
        // Gate starts closed (true) so target = live always → floor tracks signal
        val filter = NoiseFilter(44100, true)
        val level = 0.05f

        // Converge the floor over many iterations (FLOOR_ATTACK = 0.002; ~500 steps to reach 0.028)
        repeat(5000) {
            val b = FloatArray(8) { level }
            filter.processBands(b)
        }

        // After convergence, band_floor ≈ level; output = max(level - level*1.8, 0) = 0
        val result = FloatArray(8) { level }
        filter.processBands(result)
        for (i in result.indices) {
            assertTrue("Band $i output should be near 0 after floor convergence, got ${result[i]}",
                result[i] < 0.005f)
        }
    }

    // ── (d) disabled is exact no-op ───────────────────────────────────────────

    @Test
    fun `processSamples is exact no-op when disabled`() {
        val filter = NoiseFilter(44100, false)
        val original = FloatArray(512) { it.toFloat() / 512f }
        val copy = original.copyOf()
        val rmsRet = filter.processSamples(copy)

        assertEquals("processSamples must return 0f when disabled", 0f, rmsRet, 0f)
        assertArrayEquals("samples must be unchanged when disabled", original, copy, 0f)
    }

    @Test
    fun `processBands is exact no-op when disabled`() {
        val filter = NoiseFilter(44100, false)
        val original = FloatArray(8) { (it + 1) * 0.1f }
        val copy = original.copyOf()
        filter.processBands(copy)

        assertArrayEquals("bands must be unchanged when disabled", original, copy, 0f)
    }

    @Test
    fun `setEnabled false then re-read disabled state is no-op`() {
        val filter = NoiseFilter(44100, true)
        // Warm up so state is non-zero
        val warm = FloatArray(1024) { 0.5f }
        filter.processSamples(warm)

        filter.enabled = false

        val samples = FloatArray(256) { 0.3f }
        val ret = filter.processSamples(samples)
        assertEquals("disabled filter must return 0f", 0f, ret, 0f)
        assertTrue("disabled filter must leave samples unchanged",
            samples.all { it == 0.3f })
    }

    // ── setEnabled reset clears internal state ────────────────────────────────

    @Test
    fun `setEnabled false resets state so re-enable starts clean`() {
        val filter = NoiseFilter(44100, true)
        // Drive gate open with loud signal
        val loud = FloatArray(2048) { i ->
            (0.5f * sin(2.0 * PI * 1000.0 * i / 44100).toFloat())
        }
        filter.processSamples(loud)

        // Disable (resets all state)
        filter.enabled = false
        filter.enabled = true

        // First call on quiet signal — gate must start closed (reset state)
        val quiet = FloatArray(2048) { 0.001f }
        val inputRms = sqrt(quiet.map { it * it }.average()).toFloat()
        filter.processSamples(quiet)
        val outputRms = sqrt(quiet.map { it * it }.average()).toFloat()

        // Gate should be closed → output ≈ 0.05 × input
        assertTrue("After reset, gate should close on quiet signal; ratio=${outputRms / inputRms}",
            outputRms < inputRms * 0.1f)
    }
}
