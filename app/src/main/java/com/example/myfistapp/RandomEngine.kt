package com.example.myfistapp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random

/**
 * Timer-driven random parameter automation.
 * Applies a random parameter change at intervals determined by intensity.
 * Does NOT write to DataStore — saved settings are unaffected.
 */
class RandomEngine(private val applyChange: (Random) -> Unit) {
    @Volatile var enabled: Boolean = false
    @Volatile var intensity: Float = 0.5f

    private val random = Random()
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        stop()
        job = scope.launch {
            while (isActive) {
                delay(computeIntervalMs(intensity))
                if (enabled) applyChange(random)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    // ── Export: driven by video time, not wall-clock ──────────────────────────

    private var nextRandomTimeMs: Long = 0L

    /** Called once per encoded frame; fires only when enough video time has elapsed. */
    fun tickWithVideoTime(videoTimeMs: Long) {
        if (!enabled) return
        if (videoTimeMs >= nextRandomTimeMs) {
            applyChange(random)
            nextRandomTimeMs = videoTimeMs + computeIntervalMs(intensity)
        }
    }

    private fun computeIntervalMs(intensity: Float): Long {
        // intensity 0 → 5000 ms between changes; intensity 1 → 200 ms
        val maxMs = 5000.0
        val minMs = 200.0
        return (maxMs - (maxMs - minMs) * intensity.coerceIn(0f, 1f)).toLong()
    }
}
