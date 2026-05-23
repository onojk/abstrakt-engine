package com.onojk.abstrakt.audio

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the tempo-adaptive AdaptiveCooldown. All time values are
 * caller-supplied floats in seconds — no wall-clock calls, deterministic.
 */
class AdaptiveCooldownTest {

    /** Record [count] fires at [intervalSec] apart, starting at t=0. Returns last fire time. */
    private fun feedFires(cd: AdaptiveCooldown, count: Int, intervalSec: Float): Float {
        var t = 0f
        cd.recordFire(t)
        repeat(count) {
            t += intervalSec
            cd.recordFire(t)
        }
        return t
    }

    // ── (a) Empty history → 120 ms default ───────────────────────────────────

    @Test
    fun `empty history uses 120ms default cooldown`() {
        val cd = AdaptiveCooldown()
        cd.recordFire(0f)
        // Within 120ms → blocked
        assertFalse("should block before default 120ms", cd.canFire(0.119f))
        // At 120ms → allowed
        assertTrue("should allow at exactly 120ms default", cd.canFire(0.120f))
    }

    // ── (b) Steady 0.5s intervals (120 BPM) → cooldown 0.20s ─────────────────

    @Test
    fun `steady 500ms intervals produce 200ms cooldown`() {
        val cd = AdaptiveCooldown()
        val lastT = feedFires(cd, AdaptiveCooldown.HISTORY_CAP, 0.5f)

        // median = 0.5s; 0.5 * 0.40 = 0.20s (within [0.080, 0.220])
        // Use ±5ms margin around the 200ms boundary to stay clear of float rounding.
        assertFalse("should block at 195ms", cd.canFire(lastT + 0.195f))
        assertTrue("should allow at 205ms",  cd.canFire(lastT + 0.205f))
    }

    // ── (c) Steady 0.125s intervals (480 BPM-ish) → floor clamp 0.080s ───────

    @Test
    fun `fast intervals clamp cooldown to floor 80ms`() {
        val cd = AdaptiveCooldown()
        // 0.125 * 0.40 = 0.05s → below floor 0.080 → clamped to 0.080s
        val lastT = feedFires(cd, AdaptiveCooldown.HISTORY_CAP, 0.125f)

        assertFalse("should block at 79ms",  cd.canFire(lastT + 0.079f))
        assertTrue("should allow at 80ms",   cd.canFire(lastT + 0.080f))
    }

    // ── (d) Steady 1.0s intervals (60 BPM) → ceil clamp 0.220s ──────────────

    @Test
    fun `slow intervals clamp cooldown to ceil 220ms`() {
        val cd = AdaptiveCooldown()
        // 1.0 * 0.40 = 0.40s → above ceil 0.220 → clamped to 0.220s
        val lastT = feedFires(cd, AdaptiveCooldown.HISTORY_CAP, 1.0f)

        assertFalse("should block at 219ms", cd.canFire(lastT + 0.219f))
        assertTrue("should allow at 220ms",  cd.canFire(lastT + 0.220f))
    }

    // ── (e) Out-of-range intervals are not added to history ───────────────────

    @Test
    fun `intervals below floor are not pushed to history`() {
        val cd = AdaptiveCooldown()
        cd.recordFire(0f)
        // 0.050s < FLOOR_SEC (0.080) → should be ignored
        cd.recordFire(0.050f)
        // History is still empty → cooldown = 0.120 (default)
        assertFalse("should still use default 120ms", cd.canFire(0.050f + 0.119f))
        assertTrue("should allow at 120ms default",   cd.canFire(0.050f + 0.120f))
    }

    @Test
    fun `intervals above max are not pushed to history`() {
        val cd = AdaptiveCooldown()
        cd.recordFire(0f)
        // 2.5s > MAX_INTERVAL_SEC (2.0) → should be ignored
        cd.recordFire(2.5f)
        // History still empty → cooldown = 0.120 (default). ±5ms margin.
        assertFalse("should still use default 120ms", cd.canFire(2.5f + 0.115f))
        assertTrue("should allow past 120ms default", cd.canFire(2.5f + 0.125f))
    }

    @Test
    fun `boundary interval at exactly floor is accepted`() {
        val cd = AdaptiveCooldown()
        // Fill with the floor boundary value to confirm it IS accepted into history.
        // If ignored, history stays empty → default 120ms cooldown.
        // If accepted, median = 0.080; 0.080 * 0.40 = 0.032 → clamps to floor 0.080s.
        // Either way cooldown ≤ 0.120s; we check the history is non-empty by verifying
        // the cooldown is NOT the 0.120 default (fires well before 120ms, blocked near 80ms).
        val lastT = feedFires(cd, AdaptiveCooldown.HISTORY_CAP, AdaptiveCooldown.FLOOR_SEC)
        // If history accepted floor intervals: cooldown = 0.080s → allow at 85ms
        assertTrue("should allow past floor cooldown (80ms) after floor-boundary intervals",
            cd.canFire(lastT + 0.085f))
    }

    // ── (f) reset() clears all state ─────────────────────────────────────────

    @Test
    fun `reset clears history and last-fire so canFire is immediately true`() {
        val cd = AdaptiveCooldown()
        feedFires(cd, AdaptiveCooldown.HISTORY_CAP, 0.3f)
        cd.reset()
        // After reset: lastFireSec = null, history empty → can fire at any time
        assertTrue("canFire must be true immediately after reset at t=0",   cd.canFire(0f))
        assertTrue("canFire must be true immediately after reset at t=0.001f", cd.canFire(0.001f))
    }

    @Test
    fun `reset clears history so next cooldown reverts to default`() {
        val cd = AdaptiveCooldown()
        // Build history with slow 1s intervals (would give 220ms cooldown)
        feedFires(cd, AdaptiveCooldown.HISTORY_CAP, 1.0f)
        cd.reset()
        cd.recordFire(0f)
        // History cleared → back to 120ms default
        assertFalse("should block before 120ms after reset", cd.canFire(0.119f))
        assertTrue("should allow at 120ms after reset",      cd.canFire(0.120f))
    }

    // ── Median index matches deck: sorted[size/2] ─────────────────────────────

    @Test
    fun `median uses upper-middle index (size div 2)`() {
        // Feed a mixed history so that sorted[size/2] is not the same as sorted[0]
        // History (insertion order): 0.3, 0.5, 0.3, 0.5, 0.3, 0.5, 0.3, 0.5
        // sorted: [0.3, 0.3, 0.3, 0.3, 0.5, 0.5, 0.5, 0.5]
        // size=8, index=4 → 0.5; cooldown = 0.5*0.4 = 0.20s
        val cd = AdaptiveCooldown()
        var t = 0f
        cd.recordFire(t)
        for (i in 1..8) {
            t += if (i % 2 == 0) 0.5f else 0.3f
            cd.recordFire(t)
        }
        // If median were lower-middle (index 3 → 0.3): cooldown = 0.3*0.4 = 0.12s
        // If median is upper-middle (index 4 → 0.5): cooldown = 0.5*0.4 = 0.20s
        assertFalse("cooldown should be ~0.20s (upper median), not 0.12s",
            cd.canFire(t + 0.150f))
        assertTrue("cooldown should be ~0.20s (upper median)",
            cd.canFire(t + 0.201f))
    }
}
