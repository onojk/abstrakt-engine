package com.onojk.abstrakt.audio

import kotlin.math.*

class NoiseFilter(sampleRate: Int, enabled: Boolean) {

    var enabled: Boolean = enabled
        set(value) { field = value; if (!value) reset() }

    // ── HPF state (one-pole, fc = 80 Hz) ─────────────────────────────────────
    private val hpAlpha: Float = exp(-2.0 * PI * 80.0 / sampleRate).toFloat()
    private var hpPrevIn  = 0f
    private var hpPrevOut = 0f

    // ── RMS gate state ────────────────────────────────────────────────────────
    private var rmsSmoothed = 0f
    private var gateClosed  = true

    // ── Per-band spectral subtraction state (8 bands) ─────────────────────────
    private val bandFloor = FloatArray(8)

    companion object {
        // Raise both thresholds if silent-room mic noise keeps gate open (micro-shake).
        // Lower if quiet music stops triggering reactivity. See also AudioAnalyzer.MIN_BAND_PEAK.
        const val GATE_OPEN_THRESH  = 0.040f  // was 0.020; tuned up for SM-S931U noise floor
        const val GATE_CLOSE_THRESH = 0.020f  // was 0.010
        private const val GATE_CLOSED_GAIN  = 0.05f
        private const val FLOOR_ATTACK      = 0.002f
        private const val FLOOR_RELEASE     = 0.0005f
    }

    /**
     * In-place HPF + RMS gate on [samples]. Returns post-gate rms_smoothed.
     * No-op returning 0f when disabled or samples is empty.
     */
    fun processSamples(samples: FloatArray): Float {
        if (!enabled || samples.isEmpty()) return 0f

        // HPF: y[n] = a * (y[n-1] + x[n] - x[n-1])
        for (i in samples.indices) {
            val x = samples[i]
            val y = hpAlpha * (hpPrevOut + x - hpPrevIn)
            hpPrevIn  = x
            hpPrevOut = y
            samples[i] = y
        }

        // RMS of post-HPF buffer
        var sumSq = 0f
        for (s in samples) sumSq += s * s
        val rms = sqrt(sumSq / samples.size)

        // RMS smoothing with asymmetric attack/release
        val a = if (rms > rmsSmoothed) 0.5f else 0.05f
        rmsSmoothed = rmsSmoothed * (1f - a) + rms * a

        // Gate hysteresis
        if (rmsSmoothed > GATE_OPEN_THRESH)  gateClosed = false
        if (rmsSmoothed < GATE_CLOSE_THRESH) gateClosed = true

        if (gateClosed) {
            for (i in samples.indices) samples[i] *= GATE_CLOSED_GAIN
        }

        return rmsSmoothed
    }

    /**
     * In-place 8-band spectral subtraction. Must be called after [processSamples]
     * so gate state is current. No-op when disabled. Requires bands.size == 8.
     */
    fun processBands(bands: FloatArray) {
        require(bands.size == 8)
        if (!enabled) return

        for (i in bands.indices) {
            val live = bands[i]
            val target = if (gateClosed || live < bandFloor[i] * 1.5f) live
                         else bandFloor[i] * 0.999f
            val rate = if (target > bandFloor[i]) FLOOR_ATTACK else FLOOR_RELEASE
            bandFloor[i] = bandFloor[i] * (1f - rate) + target * rate
            bands[i] = maxOf(live - bandFloor[i] * 1.8f, 0f)
        }
    }

    private fun reset() {
        hpPrevIn    = 0f
        hpPrevOut   = 0f
        rmsSmoothed = 0f
        gateClosed  = true
        bandFloor.fill(0f)
    }
}
