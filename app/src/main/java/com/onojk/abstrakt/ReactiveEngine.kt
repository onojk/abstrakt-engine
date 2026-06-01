package com.onojk.abstrakt

import com.onojk.abstrakt.audio.AudioSnapshot
import kotlin.math.max

/**
 * Beat-driven painter cycling. Unlike PartyEngine (which fires probabilistically on every beat
 * and randomizes parameters), ReactiveEngine fires only on STRONG beats above a dynamic
 * baseline, with a cooldown — and only changes the painter. The result is "subtle and musical":
 * the painter follows the song's energy peaks instead of constantly remixing.
 *
 * Ported from abstrakt-deck (Linux). Does NOT write to DataStore — saved settings are unaffected.
 *
 * Intensity ∈ [0,1] maps to:
 *   thresholdMult: 2.5 (intensity=0, fires only on big peaks) → 1.05 (intensity=1, fires often)
 *   cooldownMs:    8000 (intensity=0)                         → 500 (intensity=1)
 */
class ReactiveEngine(private val onTrigger: () -> Unit) {

    @Volatile var enabled: Boolean = false
    @Volatile var intensity: Float = 0.5f

    private var bassMidBaseline: Float = 0f      // slow EMA (long-term "loudness floor")
    private var bassMidSmoothed: Float = 0f      // fast EMA (transient peak follower)
    private var lastTriggerMs: Long = 0L

    /**
     * Call on every audio snapshot from the live path (file playback or mic).
     * Returns true if a trigger fired (test hook).
     */
    fun onSnapshot(snap: AudioSnapshot, nowMs: Long = System.currentTimeMillis()): Boolean {
        // Always update the baselines so they're "warmed up" if the user toggles on mid-song.
        val bassMid = max(snap.beatLow, snap.beatMid)
        bassMidBaseline = bassMidBaseline * 0.99f + bassMid * 0.01f
        bassMidSmoothed = bassMidSmoothed * 0.70f + bassMid * 0.30f

        if (!enabled) return false

        val thresholdMult = lerp(2.5f, 1.05f, intensity)
        val cooldownMs    = lerp(8000f, 500f, intensity).toLong()
        if (nowMs - lastTriggerMs < cooldownMs) return false

        val triggerLevel = bassMidBaseline * thresholdMult
        if (bassMidBaseline > 0.001f && bassMidSmoothed > triggerLevel) {
            onTrigger()
            lastTriggerMs = nowMs
            return true
        }
        return false
    }

    /** Reset state — call when audio source changes (new file loaded, mic switched). */
    fun reset() {
        bassMidBaseline = 0f
        bassMidSmoothed = 0f
        lastTriggerMs   = 0L
    }

    private fun lerp(a: Float, b: Float, t: Float): Float {
        val tt = t.coerceIn(0f, 1f)
        return a + (b - a) * tt
    }
}
