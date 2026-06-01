package com.onojk.abstrakt

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random

private const val TAG = "RandomEngine"

/**
 * Timer-driven random parameter automation.
 * Applies a random parameter change at intervals determined by intensity.
 * Does NOT write to DataStore — saved settings are unaffected.
 *
 * On toggle off→on: fires the first change SYNCHRONOUSLY at toggle time (matching the export
 * path which fires at videoTimeMs=0). The live coroutine then continues at normal intervals.
 * Each re-enable reseeds the RNG so state is fully fresh.
 */
class RandomEngine(private val applyChange: (Random) -> Unit) {

    // (a) toggle handler: property setter logs the exact moment the flag flips
    @Volatile private var _enabled: Boolean = false
    var enabled: Boolean
        get() = _enabled
        set(value) {
            val prev = _enabled
            _enabled = value
            if (value && !prev) {
                // (c) RNG initialization — synchronous at toggle time, not on first render
                val nowMs = SystemClock.elapsedRealtime()
                Log.d(TAG, "[$nowMs ms] RANDOM_TOGGLE_ON — seeding RNG, resetting export timer")
                random.setSeed(System.currentTimeMillis())
                nextRandomTimeMs = 0L
                // (b) first execution of the random-mode render branch — synchronous here,
                // not deferred to whenever the coroutine's current delay() happens to expire
                applyChange(random)
                Log.d(TAG, "[${SystemClock.elapsedRealtime()} ms] RANDOM_FIRST_CHANGE_APPLIED")
            } else if (!value && prev) {
                Log.d(TAG, "[${SystemClock.elapsedRealtime()} ms] RANDOM_TOGGLE_OFF")
            }
        }

    @Volatile var intensity: Float = 0.5f

    private val random = Random()
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        stop()
        job = scope.launch {
            while (isActive) {
                delay(computeIntervalMs(intensity))
                if (_enabled) {
                    Log.d(TAG, "[${SystemClock.elapsedRealtime()} ms] RANDOM_TIMER_FIRE interval=${computeIntervalMs(intensity)}ms")
                    applyChange(random)
                }
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
        if (!_enabled) return
        if (videoTimeMs >= nextRandomTimeMs) {
            // (d) first video frame consumed — fires at videoTimeMs=0 on every enable
            Log.d(TAG, "[$videoTimeMs ms video] RANDOM_EXPORT_FIRE nextAt=${videoTimeMs + computeIntervalMs(intensity)}ms")
            applyChange(random)
            nextRandomTimeMs = videoTimeMs + computeIntervalMs(intensity)
        }
    }

    private fun computeIntervalMs(intensity: Float): Long {
        // intensity 0 → 5000 ms between changes; intensity 1 → 200 ms
        val maxMs = 5000.0
        val minMs = 200.0
        return (maxMs - (maxMs - minMs) * intensity.coerceIn(0f, 1F)).toLong()
    }
}
