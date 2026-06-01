package com.onojk.abstrakt.audio

import kotlin.math.*

internal val KS_MAJOR = floatArrayOf(
    6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f,
    2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f,
)
internal val KS_MINOR = floatArrayOf(
    6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f,
    2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f,
)

// Returns raw (un-normalized) accumulated magnitudes — same as deck's chromagram().
// normalizeProfile is called inside KeyTracker.processChunk on a defensive copy,
// matching deck's ordering and preventing double-normalization.
internal fun chromaFromMagnitudes(magnitudes: FloatArray, sampleRate: Int): FloatArray {
    val binHz  = sampleRate.toFloat() / FFT_SIZE.toFloat()
    val chroma = FloatArray(12)
    for (bin in magnitudes.indices) {
        val freq = bin * binHz
        if (freq < 27.5f || freq > 4186.0f) continue
        val midi = 69.0 + 12.0 * log2(freq / 440.0)
        val pc   = ((midi.roundToInt() % 12) + 12) % 12
        chroma[pc] += magnitudes[bin]
    }
    return chroma
}

// L2 norm in-place. Returns false if the vector is all-zero (caller should drop frame).
// Identical to deck's normalize_profile — L2 norm only, no mean subtraction, no stddev.
internal fun normalizeProfile(v: FloatArray): Boolean {
    var sumSq = 0.0
    for (x in v) sumSq += x.toDouble() * x
    val norm = sqrt(sumSq).toFloat()
    if (norm < 1e-9f) return false
    for (i in v.indices) v[i] /= norm
    return true
}

internal fun pearsonRotated(chroma: FloatArray, profile: FloatArray, rot: Int): Float {
    var meanC = 0f; var meanP = 0f
    for (i in 0..11) { meanC += chroma[i]; meanP += profile[i] }
    meanC /= 12f; meanP /= 12f
    var num = 0f; var ssC = 0f; var ssP = 0f
    for (i in 0..11) {
        val c = chroma[(i + rot) % 12] - meanC
        val p = profile[i] - meanP
        num += c * p; ssC += c * c; ssP += p * p
    }
    val den = sqrt(ssC * ssP)
    return if (den < 1e-9f) 0f else num / den
}

private val PITCH_CLASS_NAMES = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")

fun pitchClassName(root: Int, isMajor: Boolean): String =
    "${PITCH_CLASS_NAMES[root.coerceIn(0, 11)]} ${if (isMajor) "major" else "minor"}"

class KeyTracker(val chunkSeconds: Float) {

    private val historySize = ceil(HISTORY_SECONDS / chunkSeconds).toInt().coerceAtLeast(4)
    private val estimatePeriodChunks =
        ceil(ESTIMATE_PERIOD_SECONDS / chunkSeconds).toInt().coerceAtLeast(1)

    private val chromaHistory      = ArrayDeque<FloatArray>(historySize)
    private var chunksSinceEstimate = 0

    var chroma:     FloatArray = FloatArray(12); private set
    // deck's chroma_peak is Float (index/12.0); Android uses Int so chromaPeak * 30° = colorizeHue.
    var chromaPeak: Int        = 0;              private set

    // NOTE: deck's KeyTracker has neither confidence gating nor debouncing — it updates
    // key_root every estimate unconditionally. Android adds these because pitchToHue glides
    // hue smoothly between notes; a flapping key would visibly thrash the color. The 0.70
    // threshold and 3-hit gate keep the locked key stable enough for visual consumption.
    var lockedKey:  Pair<Int, Boolean>? = null;  private set
    var confidence: Float               = 0f;    private set

    private var candidateKey:  Pair<Int, Boolean>? = null
    private var candidateHits: Int                 = 0

    /** Feed one raw chromagram frame. Returns true if lockedKey changed. */
    fun processChunk(rawChroma: FloatArray): Boolean {
        val c = rawChroma.copyOf()
        if (!normalizeProfile(c)) return false

        chromaPeak = c.indices.maxByOrNull { c[it] } ?: 0
        chroma     = c

        chromaHistory.addLast(c)
        if (chromaHistory.size > historySize) chromaHistory.removeFirst()

        chunksSinceEstimate++
        return if (chunksSinceEstimate >= estimatePeriodChunks) {
            chunksSinceEstimate = 0
            estimateKey()
        } else {
            false
        }
    }

    private fun estimateKey(): Boolean {
        if (chromaHistory.isEmpty()) return false

        // Accumulate sliding window, then L2-normalize — mirrors deck's estimate_key.
        val acc = FloatArray(12)
        for (c in chromaHistory) for (i in 0..11) acc[i] += c[i]
        if (!normalizeProfile(acc)) return false

        var bestRoot    = 0
        var bestIsMajor = true
        var bestCorr    = Float.NEGATIVE_INFINITY
        for (rot in 0..11) {
            val corrMaj = pearsonRotated(acc, KS_MAJOR, rot)
            val corrMin = pearsonRotated(acc, KS_MINOR, rot)
            if (corrMaj > bestCorr) { bestCorr = corrMaj; bestRoot = rot; bestIsMajor = true  }
            if (corrMin > bestCorr) { bestCorr = corrMin; bestRoot = rot; bestIsMajor = false }
        }

        confidence = bestCorr
        if (bestCorr < CONFIDENCE_THRESHOLD) return false

        val candidate = Pair(bestRoot, bestIsMajor)
        if (candidate == lockedKey) { candidateKey = null; candidateHits = 0; return false }

        return if (candidate == candidateKey) {
            candidateHits++
            if (candidateHits >= CANDIDATE_HITS_REQUIRED) {
                lockedKey = candidate; candidateKey = null; candidateHits = 0; true
            } else {
                false
            }
        } else {
            candidateKey = candidate; candidateHits = 1; false
        }
    }

    fun reset() {
        chromaHistory.clear(); chunksSinceEstimate = 0
        chroma = FloatArray(12); chromaPeak = 0
        lockedKey = null; confidence = 0f
        candidateKey = null; candidateHits = 0
    }

    companion object {
        const val HISTORY_SECONDS         = 4f
        const val ESTIMATE_PERIOD_SECONDS = 0.5f
        const val CONFIDENCE_THRESHOLD    = 0.70f
        private const val CANDIDATE_HITS_REQUIRED = 3
    }
}
