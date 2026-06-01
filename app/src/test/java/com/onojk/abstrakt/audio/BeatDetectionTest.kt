package com.onojk.abstrakt.audio

import org.junit.Assert.*
import org.junit.Test

/**
 * Mirrors the spirit of deck's pitch/beat Rust unit tests.
 * All classes under test are pure Kotlin with no Android dependencies.
 */
class BeatDetectionTest {

    // ── BeatEnvelope ──────────────────────────────────────────────────────────

    @Test
    fun `BeatEnvelope trigger sets level to clamped strength`() {
        val env = BeatEnvelope()
        env.trigger(0.8f)
        assertEquals(0.8f, env.level, 1e-6f)
    }

    @Test
    fun `BeatEnvelope clamps strength above 1 and below 0`() {
        val env = BeatEnvelope()
        env.trigger(2.5f)
        assertEquals(1.0f, env.level, 1e-6f)

        env.trigger(-0.5f)
        assertEquals(0.0f, env.level, 1e-6f)
    }

    @Test
    fun `BeatEnvelope hold pins level during hold phase`() {
        val env = BeatEnvelope(holdSec = 0.05f, releaseSec = 0.18f)
        env.trigger(1.0f)
        env.advance(0.03f)                   // well within the 50ms hold
        assertEquals(1.0f, env.level, 1e-6f) // level must not change
    }

    @Test
    fun `BeatEnvelope decays to near-zero after hold plus full release`() {
        val env = BeatEnvelope(holdSec = 0.05f, releaseSec = 0.18f)
        env.trigger(1.0f)
        env.advance(0.05f)                   // consume hold exactly
        env.advance(0.18f)                   // one full release period (1 - 1 = 0)
        assertTrue("Level should be near 0, got ${env.level}", env.level < 0.05f)
    }

    @Test
    fun `BeatEnvelope reset returns level to zero`() {
        val env = BeatEnvelope()
        env.trigger(1.0f)
        env.reset()
        assertEquals(0f, env.level, 1e-6f)
    }

    // ── AdaptiveCooldown ──────────────────────────────────────────────────────

    @Test
    fun `AdaptiveCooldown allows fire before any record`() {
        val cd = AdaptiveCooldown()
        assertTrue(cd.canFire(0f))
    }

    @Test
    fun `AdaptiveCooldown blocks re-fire within default gap`() {
        // Empty history -> default cooldown = 120 ms
        val cd = AdaptiveCooldown()
        cd.recordFire(0f)
        assertFalse(cd.canFire(0.1f))   // 100ms < 120ms default
        assertFalse(cd.canFire(0.119f)) // just under the 120ms default
    }

    @Test
    fun `AdaptiveCooldown allows re-fire at and after default gap`() {
        // Empty history -> default cooldown = 120 ms
        val cd = AdaptiveCooldown()
        cd.recordFire(0f)
        assertTrue(cd.canFire(0.12f))  // exactly at 120ms default
        assertTrue(cd.canFire(0.5f))   // well past gap
    }

    @Test
    fun `AdaptiveCooldown reset re-enables immediate fire`() {
        val cd = AdaptiveCooldown()
        cd.recordFire(0f)
        cd.reset()
        assertTrue(cd.canFire(0.01f))
    }

    // ── BandBeatDetector ──────────────────────────────────────────────────────

    @Test
    fun `BandBeatDetector fires envelope on flux spike above threshold`() {
        val det = BandBeatDetector(BeatBand.Broadband, 0, FFT_BINS, historySize = 43)
        val quiet = FloatArray(FFT_BINS) { 0.01f }
        val dtSec = 0.05f

        // Prime the history with near-zero flux so average is well established
        repeat(20) { i -> det.processFlux(quiet, quiet, i * dtSec, dtSec) }

        // Spike: large jump across all bins
        val spike = FloatArray(FFT_BINS) { 5.0f }
        val lvl   = det.processFlux(spike, quiet, 20 * dtSec, dtSec)

        assertTrue("Expected envelope > 0 after flux spike, got $lvl", lvl > 0f)
    }

    @Test
    fun `BandBeatDetector does not fire on sustained level (no onset)`() {
        // If spectrum stays constant, flux = 0 every window → never fires.
        val det = BandBeatDetector(BeatBand.Broadband, 0, FFT_BINS, historySize = 43)
        val steady = FloatArray(FFT_BINS) { 3.0f }
        val dtSec = 0.05f

        var lastLevel = 0f
        repeat(30) { i ->
            lastLevel = det.processFlux(steady, steady, i * dtSec, dtSec)
        }
        assertEquals("Constant spectrum should produce zero flux and no beat", 0f, lastLevel, 1e-6f)
    }

    @Test
    fun `BandBeatDetector respects 200ms cooldown after firing`() {
        val det = BandBeatDetector(BeatBand.Broadband, 0, FFT_BINS, historySize = 43)
        val quiet = FloatArray(FFT_BINS) { 0.01f }
        val spike = FloatArray(FFT_BINS) { 5.0f }
        val dtSec = 0.05f

        repeat(20) { i -> det.processFlux(quiet, quiet, i * dtSec, dtSec) }

        // First spike — fires and sets cooldown
        val t0 = 20 * dtSec
        det.processFlux(spike, quiet, t0, dtSec)

        // Second spike 50ms later — within 200ms cooldown; envelope should NOT re-trigger to full
        det.processFlux(quiet, quiet, t0 + 0.05f, 0.05f) // advance hold
        val lvlAfterCooldown = det.processFlux(spike, quiet, t0 + 0.05f, dtSec)

        // After the hold (50ms) but before cooldown expires (200ms), envelope is decaying — not 1.0
        assertTrue("Level should be decaying, not freshly triggered", lvlAfterCooldown < 1.0f)
    }

    @Test
    fun `BandBeatDetector Low band ignores high-frequency content`() {
        val sampleRate = 44100
        val dets   = createBandDetectors(sampleRate, historySize = 20)
        val detLow = dets[BeatBand.Low.ordinal]
        val detHigh= dets[BeatBand.High.ordinal]

        val quiet = FloatArray(FFT_BINS) { 0.01f }
        val dtSec = 0.05f

        // Prime both with quiet baseline
        repeat(20) { i ->
            detLow.processFlux(quiet, quiet, i * dtSec, dtSec)
            detHigh.processFlux(quiet, quiet, i * dtSec, dtSec)
        }

        // Spike only in High band bins
        val spikeHigh = FloatArray(FFT_BINS) { k ->
            if (k >= detHigh.loBin) 5.0f else 0.01f
        }
        val t = 20 * dtSec
        val lvlLow  = detLow.processFlux(spikeHigh, quiet, t, dtSec)
        val lvlHigh = detHigh.processFlux(spikeHigh, quiet, t, dtSec)

        assertEquals("Low band must not fire on a high-freq-only spike", 0f, lvlLow, 1e-6f)
        assertTrue("High band must fire on a high-freq spike, got $lvlHigh", lvlHigh > 0f)
    }

    @Test
    fun `createBandDetectors bins are in ascending order and non-overlapping`() {
        val dets = createBandDetectors(44100, historySize = 10)
        assertEquals(BeatBand.Low.ordinal,       0)
        assertEquals(BeatBand.Broadband.ordinal, 3)

        // Each named band: loBin < hiBin
        for (d in dets) {
            assertTrue("${d.band}: loBin ${d.loBin} must be < hiBin ${d.hiBin}",
                d.loBin < d.hiBin)
        }

        // Broadband spans all bins
        val broad = dets[BeatBand.Broadband.ordinal]
        assertEquals(0, broad.loBin)
        assertEquals(FFT_BINS, broad.hiBin)
    }
}
