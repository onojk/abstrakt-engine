package com.onojk.abstrakt.audio

import kotlin.math.abs
import kotlin.math.ceil

/**
 * Tracks tempo via autocorrelation of a rolling broadband-flux history, then
 * maintains a continuous beat_phase ∈ [0, 1) via a phase-locked loop (PLL).
 *
 * Ported from deck src/audio.rs TempoTracker.
 *
 * BPM locks ~6-7 seconds after audio starts. This is intrinsic to the
 * 6-second history window required for reliable autocorrelation; a shorter
 * window produces unstable estimates at common BPMs (e.g. 60 BPM requires
 * ~6 full beat cycles to distinguish lag 60 from lag 30 or lag 120).
 * Do not lower HISTORY_SECONDS without measuring stability degradation.
 *
 * @param chunkSeconds Nominal duration of one analysis chunk in seconds.
 *   Used for (a) lag-to-BPM mapping in the autocorrelation and (b) phase
 *   advance per chunk. Pass windowDtSec for the offline path (~50 ms) or
 *   STREAMING_CHUNK_SEC (~16.7 ms) for the live mic path.
 */
class TempoTracker(val chunkSeconds: Float) {

    private val historySize: Int =
        ceil(HISTORY_SECONDS / chunkSeconds).toInt().coerceAtLeast(4)
    private val estimatePeriodChunks: Int =
        ceil(ESTIMATE_PERIOD_SECONDS / chunkSeconds).toInt().coerceAtLeast(1)

    private val fluxHistory = ArrayDeque<Float>(historySize)

    private var lockedBpm:          Float? = null
    private var lockedConfidence:   Float  = 0f
    private var chunksSinceEstimate: Int   = 0

    // PLL state
    var phase: Float = 0f
        private set
    private val pllGain = PLL_GAIN

    // Candidate debouncing (3 consistent estimates required to change a locked BPM)
    private var candidateBpm:  Float? = null
    private var candidateHits: Int    = 0

    // BPM locks ~6-7 s after audio starts (see class doc).
    val currentBpm:  Float? get() = lockedBpm
    val confidence:  Float  get() = lockedConfidence

    /** Feed one analysis chunk's broadband flux value and advance the PLL phase. */
    fun processChunk(broadbandFlux: Float) {
        fluxHistory.addLast(broadbandFlux)
        if (fluxHistory.size > historySize) fluxHistory.removeFirst()

        lockedBpm?.let { bpm ->
            phase += chunkSeconds / (60f / bpm)
            while (phase >= 1f) phase -= 1f
        }

        chunksSinceEstimate++
        if (chunksSinceEstimate >= estimatePeriodChunks
            && fluxHistory.size >= historySize
        ) {
            chunksSinceEstimate = 0
            estimateTempo()
        }
    }

    /**
     * Nudge the PLL phase toward 0 (beat anchor) on a confirmed broadband onset.
     * Has no effect until a BPM is locked.
     */
    fun onBroadbandOnset() {
        if (lockedBpm == null) return
        val error = if (phase < 0.5f) -phase else 1f - phase
        phase += error * pllGain
        while (phase >= 1f) phase -= 1f
        while (phase <  0f) phase += 1f
    }

    /** Reset all state — call when starting a new streaming session. */
    fun reset() {
        fluxHistory.clear()
        lockedBpm          = null
        lockedConfidence   = 0f
        chunksSinceEstimate = 0
        phase              = 0f
        candidateBpm       = null
        candidateHits      = 0
    }

    private fun estimateTempo() {
        val lagMin = (60f / BPM_MAX / chunkSeconds).toInt().coerceAtLeast(1)
        val lagMax = (60f / BPM_MIN / chunkSeconds).toInt()
        if (lagMax >= fluxHistory.size || lagMin >= lagMax) return

        val mean = fluxHistory.average().toFloat()

        var bestLag   = lagMin
        var bestScore = 0f
        var scoreSum  = 0f
        var scoreCount = 0
        val scores = FloatArray(lagMax - lagMin + 1)

        for (lag in lagMin..lagMax) {
            val n = fluxHistory.size - lag
            var score = 0f
            for (i in 0 until n) {
                score += (fluxHistory[i] - mean) * (fluxHistory[i + lag] - mean)
            }
            score /= n.toFloat()
            scores[lag - lagMin] = score
            scoreSum  += score.coerceAtLeast(0f)
            scoreCount++
            if (score > bestScore) { bestScore = score; bestLag = lag }
        }

        if (scoreCount == 0 || bestScore <= 0f) return

        // Tempo-doubling check: if lag/2 scores ≥ 65% of the peak, the music
        // likely has a strong off-beat emphasis (hip-hop, reggae). Prefer the
        // faster (halved) lag. Done only once — recursing to lag/4 would
        // over-fire on any regularly-structured signal.
        val halfLag = bestLag / 2
        if (halfLag >= lagMin && halfLag <= lagMax) {
            val halfScore = scores[halfLag - lagMin]
            if (halfScore >= bestScore * 0.65f) {
                bestLag   = halfLag
                bestScore = halfScore
            }
        }

        val meanScore  = scoreSum / scoreCount.toFloat()
        val confidence = if (meanScore > 1e-9f) bestScore / meanScore else 0f

        if (confidence < CONFIDENCE_THRESHOLD) {
            lockedConfidence = confidence
            return
        }

        val bpmCandidate = 60f / (bestLag.toFloat() * chunkSeconds)
        val current      = lockedBpm

        if (current == null) {
            lockedBpm        = bpmCandidate
            lockedConfidence = confidence
            phase            = 0f
            candidateBpm     = null
            candidateHits    = 0
        } else {
            val drift = abs(bpmCandidate - current) / current
            if (drift < 0.03f) {
                // Candidate is consistent with current lock — refresh confidence.
                lockedConfidence = confidence
                candidateBpm     = null
                candidateHits    = 0
            } else {
                // Candidate differs from lock; require 3 consistent estimates to switch.
                val prev = candidateBpm
                if (prev != null && abs(prev - bpmCandidate) / prev < 0.05f) {
                    candidateHits++
                    if (candidateHits >= CANDIDATE_HITS_REQUIRED) {
                        lockedBpm        = bpmCandidate
                        lockedConfidence = confidence
                        candidateBpm     = null
                        candidateHits    = 0
                    }
                } else {
                    candidateBpm  = bpmCandidate
                    candidateHits = 1
                }
            }
        }
    }

    companion object {
        const val BPM_MIN                  = 60f
        const val BPM_MAX                  = 180f
        const val CONFIDENCE_THRESHOLD     = 1.8f
        const val HISTORY_SECONDS          = 6f
        const val ESTIMATE_PERIOD_SECONDS  = 0.25f
        const val PLL_GAIN                 = 0.10f
        private const val CANDIDATE_HITS_REQUIRED = 3

        // Nominal chunk duration for the live streaming path (~60 fps render loop).
        const val STREAMING_CHUNK_SEC = 1f / 60f
    }
}
