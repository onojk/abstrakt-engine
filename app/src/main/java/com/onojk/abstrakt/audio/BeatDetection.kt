package com.onojk.abstrakt.audio

// ── Band definitions ──────────────────────────────────────────────────────────

enum class BeatBand { Low, Mid, High, Broadband }

/** Hz boundaries for each beat band (mirrors deck's 4-band model in audio.rs). */
internal val BAND_BEAT_HZ: Map<BeatBand, Pair<Float, Float>> = mapOf(
    BeatBand.Low       to (60f   to 250f),
    BeatBand.Mid       to (250f  to 2000f),
    BeatBand.High      to (2000f to 16000f),
    BeatBand.Broadband to (0f    to Float.MAX_VALUE),
)

// ── BeatEnvelope ──────────────────────────────────────────────────────────────

/**
 * Attack/hold/release envelope for a single beat event.
 *
 * deck's BeatEnvelope has a separate 20ms attack ramp; on Android we collapse
 * it into the hold phase since 20ms ≈ 1 frame at 50fps and the rising edge is
 * invisible. Behavior matches deck within sub-frame precision.
 */
class BeatEnvelope(
    @Suppress("unused") val attackSec: Float = 0.020f,   // collapsed into hold; kept for API symmetry
    val holdSec:    Float = 0.050f,
    val releaseSec: Float = 0.180f,
) {
    var level: Float = 0f
        private set
    private var holdRemaining: Float = 0f

    /** Trigger the envelope at [strength] ∈ [0, 1], resetting the hold timer. */
    fun trigger(strength: Float) {
        level = strength.coerceIn(0f, 1f)
        holdRemaining = holdSec
    }

    /**
     * Advance the envelope by [dtSec] seconds.
     * During hold, [level] is pinned at its triggered value.
     * After hold expires, [level] decays exponentially toward zero.
     */
    fun advance(dtSec: Float) {
        if (holdRemaining > 0f) {
            holdRemaining = (holdRemaining - dtSec).coerceAtLeast(0f)
        } else if (level > 0f) {
            val decay = 1f - dtSec / releaseSec
            level = (level * decay.coerceAtLeast(0f)).coerceAtLeast(0f)
        }
    }

    fun reset() {
        level = 0f
        holdRemaining = 0f
    }
}

// ── AdaptiveCooldown ──────────────────────────────────────────────────────────

/** Prevents re-triggering within [minGapSec] seconds of the last fire. */
class AdaptiveCooldown(val minGapSec: Float = 0.200f) {
    private var lastFireSec: Float = -Float.MAX_VALUE

    fun canFire(nowSec: Float): Boolean = nowSec - lastFireSec >= minGapSec
    fun recordFire(nowSec: Float) { lastFireSec = nowSec }
    fun reset() { lastFireSec = -Float.MAX_VALUE }
}

// ── BandBeatDetector ──────────────────────────────────────────────────────────

/**
 * Per-band onset detector: maintains a sliding flux history for adaptive
 * thresholding, fires a [BeatEnvelope], and enforces an [AdaptiveCooldown].
 *
 * @param band        The frequency band this detector watches.
 * @param loBin       First FFT bin in this band (inclusive); pre-computed by caller.
 * @param hiBin       Last FFT bin in this band (exclusive); pre-computed by caller.
 * @param historySize Number of windows to retain for the adaptive baseline.
 *                    Pass 43 for offline (~50 ms windows → 6 s at 7.2 windows/s).
 *                    Pass ~360 for streaming (~16.7 ms frames at 60 fps → 6 s).
 */
class BandBeatDetector(
    val band: BeatBand,
    internal val loBin: Int,
    internal val hiBin: Int,
    historySize: Int,
) {
    private val maxHistory = historySize.coerceAtLeast(4)
    private val fluxHist   = ArrayDeque<Float>(maxHistory)
    val envelope           = BeatEnvelope()
    private val cooldown   = AdaptiveCooldown()

    /**
     * Compute this band's half-wave-rectified spectral flux (positive bin increases only),
     * update the adaptive threshold, and fire the envelope if onset conditions are met.
     *
     * [spectrum] and [prevSpec] are full-magnitude FFT arrays (not half-wave rectified
     * at the spectrum level — the half-wave rect is applied to the *flux* diff here).
     *
     * @param nowSec  Current time in seconds (monotonic, relative to a stable origin).
     * @param dtSec   Duration of this analysis window in seconds (for envelope advance).
     * @return Current envelope level ∈ [0, 1].
     */
    fun processFlux(
        spectrum: FloatArray,
        prevSpec: FloatArray,
        nowSec:   Float,
        dtSec:    Float,
    ): Float {
        var bandFlux = 0f
        for (k in loBin until hiBin) {
            val diff = spectrum[k] - prevSpec[k]
            if (diff > 0f) bandFlux += diff
        }

        fluxHist.addLast(bandFlux)
        if (fluxHist.size > maxHistory) fluxHist.removeFirst()

        val avg       = if (fluxHist.size < 2) 0f else fluxHist.average().toFloat()
        val threshold = avg * THRESHOLD_MULT

        if (bandFlux > threshold && bandFlux > MIN_FLUX && cooldown.canFire(nowSec)) {
            val strength = (bandFlux / threshold.coerceAtLeast(1e-6f)).coerceAtMost(3f) / 3f
            envelope.trigger(strength)
            cooldown.recordFire(nowSec)
        }

        envelope.advance(dtSec)
        return envelope.level
    }

    fun reset() {
        fluxHist.clear()
        envelope.reset()
        cooldown.reset()
    }

    companion object {
        private const val THRESHOLD_MULT = 1.5f
        const val MIN_FLUX               = 0.5f
    }
}

// ── Factory ───────────────────────────────────────────────────────────────────

/**
 * Create one [BandBeatDetector] per [BeatBand] with [loBin]/[hiBin] pre-computed
 * from [sampleRate]. Returned array is indexed by [BeatBand.ordinal].
 *
 * Use [historySize]=43 for the offline 50 ms window path.
 * Use [historySize]=360 for the streaming 60 fps path.
 */
internal fun createBandDetectors(sampleRate: Int, historySize: Int): Array<BandBeatDetector> =
    BeatBand.entries.map { band ->
        val (loHz, hiHz) = BAND_BEAT_HZ[band]!!
        val loBin = if (loHz <= 0f) 0
                    else hzToBin(loHz, sampleRate)
        val hiBin = if (hiHz >= Float.MAX_VALUE / 2f) FFT_BINS
                    else (hzToBin(hiHz, sampleRate) + 1).coerceAtMost(FFT_BINS)
        BandBeatDetector(band, loBin, hiBin, historySize)
    }.toTypedArray()
