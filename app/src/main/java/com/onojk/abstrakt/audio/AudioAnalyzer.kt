package com.onojk.abstrakt.audio

import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.*

data class AudioSnapshot(
    val bands: FloatArray,        // 8 values in [0,1]; logarithmic frequency bands (see BAND_EDGES_HZ)
    val peak: Float,
    val isBeat: Boolean,          // true when any per-band envelope is active (backward compat)
    val beatLow: Float       = 0f,  // BeatEnvelope level for Low band      (60-250 Hz)  ∈ [0,1]
    val beatMid: Float       = 0f,  // BeatEnvelope level for Mid band      (250-2k Hz)  ∈ [0,1]
    val beatHigh: Float      = 0f,  // BeatEnvelope level for High band     (2k-16k Hz)  ∈ [0,1]
    val beatBroadband: Float = 0f,  // BeatEnvelope level for Broadband                  ∈ [0,1]
    // BPM locks ~6-7 s after audio starts (intrinsic to the 6-second autocorrelation window).
    val currentBpm:    Float? = null, // null until confidence threshold is reached
    val beatPhase:     Float  = 0f,   // PLL phase ∈ [0, 1); 0 = beat anchor, advances to next
    val bpmConfidence: Float  = 0f,   // autocorrelation peak / mean; threshold = 1.8
    // Key detection — locks ~5-6 s after audio starts (4 s history + 3-hit debounce gate).
    val chroma:       FloatArray = FloatArray(12), // L2-normalized 12-pitch-class profile
    val chromaPeak:   Int        = 0,              // dominant pitch class 0–11 (C=0); hue = peak*30°
    val keyRoot:      Int?       = null,           // null until confidence gate passes; 0=C…11=B
    val keyIsMajor:   Boolean    = true,
    val keyConfidence: Float     = 0f,             // Pearson r of best-matching K-S profile
    val keyChanged:   Boolean    = false,          // true on the chunk when lockedKey transitions
)

fun snapshotAt(audioFile: AudioFile, playbackFraction: Float): AudioSnapshot {
    val n = audioFile.rmsEnvelope.size
    if (n == 0) return AudioSnapshot(FloatArray(8), 0f, false)
    val w        = (playbackFraction * n).toInt().coerceIn(0, n - 1)
    val keyRaw   = audioFile.keyRoot[w]
    return AudioSnapshot(
        bands         = audioFile.smoothedBands[w].copyOf(),
        peak          = audioFile.rmsEnvelope[w],
        isBeat        = audioFile.beatFlags[w],
        beatLow       = audioFile.beatLow[w],
        beatMid       = audioFile.beatMid[w],
        beatHigh      = audioFile.beatHigh[w],
        beatBroadband = audioFile.beatBroadband[w],
        currentBpm    = audioFile.bpm,
        beatPhase     = audioFile.beatPhase[w],
        bpmConfidence = audioFile.bpmConfidence,
        chroma        = audioFile.chroma[w].copyOf(),
        chromaPeak    = audioFile.chromaPeak[w],
        keyRoot       = if (keyRaw == -1) null else keyRaw,
        keyIsMajor    = audioFile.keyIsMajor[w],
        keyConfidence = audioFile.keyConfidence[w],
        keyChanged    = audioFile.keyChanged[w],
    )
}

fun analyzeOffline(audioFile: AudioFile, totalFrames: Int): List<AudioSnapshot> {
    if (totalFrames <= 0) return emptyList()
    return List(totalFrames) { i ->
        val fraction = if (totalFrames > 1) i.toFloat() / (totalFrames - 1) else 0f
        snapshotAt(audioFile, fraction)
    }
}

// Streaming PCM ring buffer — fed by MicCapture, consumed by the render loop.
class StreamingAnalyzer {
    private val ringSize = 8192
    private val ring     = ShortArray(ringSize)
    private var cursor   = 0
    private var fill     = 0

    @Volatile var sampleRate: Int = 44100
    @Volatile var channels: Int   = 1
    @Volatile var active: Boolean = false

    private val fftEngine = FloatFFT_1D(FFT_SIZE.toLong())
    private val fftBuf    = FloatArray(FFT_SIZE)
    private var prevSpec  = FloatArray(FFT_BINS)
    private val bandEma   = FloatArray(8)
    private val bandPeak  = FloatArray(8) { 100f }  // AGC: decays toward actual peaks

    // Per-band onset detectors — historySize=360 ≈ 6 s at 60 fps × 16.7 ms/frame
    private companion object { private const val STREAMING_HISTORY = 360 }
    private var bandDetectors = createBandDetectors(44100, STREAMING_HISTORY)

    // Tempo tracker — nominal chunk = 1/60 s (render-loop call rate).
    private val tempoTracker = TempoTracker(TempoTracker.STREAMING_CHUNK_SEC)

    // Key tracker — same nominal chunk duration as tempo tracker.
    private val keyTracker = KeyTracker(TempoTracker.STREAMING_CHUNK_SEC)

    // Wall-clock origin for nowSec passed to AdaptiveCooldown; reset in beginStreaming.
    private var nanoBase: Long = 0L
    // Previous snapshot time for dtSec envelope advance.
    private var lastNanos: Long = 0L

    fun beginStreaming(sampleRate: Int, channels: Int) {
        this.sampleRate = sampleRate
        this.channels   = channels
        synchronized(this) { ring.fill(0); cursor = 0; fill = 0 }
        prevSpec.fill(0f)
        bandEma.fill(0f)
        bandPeak.fill(100f)
        nanoBase      = 0L
        lastNanos     = 0L
        bandDetectors = createBandDetectors(sampleRate, STREAMING_HISTORY)
        tempoTracker.reset()
        keyTracker.reset()
        active = true
    }

    fun pushSamples(pcm: ShortArray, length: Int) {
        synchronized(this) {
            val n = minOf(length, pcm.size)
            for (i in 0 until n) {
                ring[cursor] = pcm[i]
                cursor = (cursor + 1) % ringSize
            }
            fill = minOf(fill + n, ringSize)
        }
    }

    fun streamingSnapshot(): AudioSnapshot {
        val ringSnap: ShortArray
        val curSnap: Int
        val fillSnap: Int
        val ch: Int
        val sr: Int
        synchronized(this) {
            if (fill == 0) return AudioSnapshot(FloatArray(8), 0f, false)
            ringSnap = ring.copyOf()
            curSnap  = cursor
            fillSnap = fill
            ch       = channels
            sr       = sampleRate
        }

        // ── Extract most-recent FFT_SIZE mono frames from the ring ────────────
        fftBuf.fill(0f)
        val neededInterleaved  = FFT_SIZE * ch
        val availInterleaved   = minOf(fillSnap, neededInterleaved)
        val monoFrames         = availInterleaved / ch
        val fftOffset          = FFT_SIZE - monoFrames  // zero-pad at the start

        for (j in 0 until monoFrames) {
            var s = 0.0
            for (c in 0 until ch) {
                val ringIdx = ((curSnap - availInterleaved + j * ch + c) + ringSize * 2) % ringSize
                s += ringSnap[ringIdx].toDouble()
            }
            val sample = (s / ch / 32768.0).toFloat().coerceIn(-1f, 1f)
            val pos    = fftOffset + j
            val hann   = (0.5 * (1.0 - cos(2.0 * PI * pos / (FFT_SIZE - 1)))).toFloat()
            fftBuf[pos] = sample * hann
        }

        fftEngine.realForward(fftBuf)
        val spectrum = spectrumMagnitudes(fftBuf)

        // ── Wall-clock time tracking (relative origin avoids Float precision loss) ──
        val nowNanos = System.nanoTime()
        if (nanoBase == 0L) { nanoBase = nowNanos; lastNanos = nowNanos }
        val nowSec = (nowNanos - nanoBase).toFloat() / 1_000_000_000f
        val dtSec  = ((nowNanos - lastNanos).toFloat() / 1_000_000_000f).coerceIn(0.001f, 0.1f)
        lastNanos  = nowNanos

        // ── Per-band onset detection (reads prevSpec before update below) ─────
        val lvLow   = bandDetectors[BeatBand.Low.ordinal]
                          .processFlux(spectrum, prevSpec, nowSec, dtSec)
        val lvMid   = bandDetectors[BeatBand.Mid.ordinal]
                          .processFlux(spectrum, prevSpec, nowSec, dtSec)
        val lvHigh  = bandDetectors[BeatBand.High.ordinal]
                          .processFlux(spectrum, prevSpec, nowSec, dtSec)

        // Broadband: capture envelope before onset so we can detect fresh triggers for PLL.
        val broadbandDet = bandDetectors[BeatBand.Broadband.ordinal]
        val bbEnvBefore  = broadbandDet.envelope.level
        val lvBroad      = broadbandDet.processFlux(spectrum, prevSpec, nowSec, dtSec)

        // Feed broadband flux to tempo estimator BEFORE onset check (mirrors deck ordering).
        tempoTracker.processChunk(broadbandDet.lastFlux)
        // 0.2f: empirical threshold — a fresh trigger always jumps the envelope by ≥ 0.33
        // (minimum strength 1/3 × 3 = 1 → hold at ~0.33+); 0.2f cleanly separates
        // fresh triggers from decay.
        if (lvBroad > bbEnvBefore + 0.2f) tempoTracker.onBroadbandOnset()

        prevSpec = spectrum

        // ── Key / pitch detection ─────────────────────────────────────────────
        val rawChroma  = chromaFromMagnitudes(spectrum, sr)
        val keyChanged = keyTracker.processChunk(rawChroma)
        val lockedKey  = keyTracker.lockedKey

        val isBeat = maxOf(lvLow, lvMid, lvHigh, lvBroad) > 0f

        // ── 8 bands with AGC normalization + EMA ─────────────────────────────
        val rawBands = bandEnergies(spectrum, sr)
        val resultBands = FloatArray(8) { b ->
            bandPeak[b] = maxOf(bandPeak[b] * 0.999f, rawBands[b])
            val norm = (rawBands[b] / bandPeak[b]).coerceIn(0f, 1f)
            bandEma[b] = 0.7f * bandEma[b] + 0.3f * norm
            bandEma[b]
        }

        // ── RMS for peak uniform ──────────────────────────────────────────────
        val peakN = minOf(fillSnap, ringSize)
        var sumSq = 0.0
        repeat(peakN) { i ->
            val idx = ((curSnap - peakN + i) + ringSize * 2) % ringSize
            val v = ringSnap[idx] / 32768.0
            sumSq += v * v
        }
        val peak = sqrt(sumSq / peakN).toFloat().coerceIn(0f, 1f)

        return AudioSnapshot(
            bands         = resultBands,
            peak          = peak,
            isBeat        = isBeat,
            beatLow       = lvLow,
            beatMid       = lvMid,
            beatHigh      = lvHigh,
            beatBroadband = lvBroad,
            currentBpm    = tempoTracker.currentBpm,
            beatPhase     = tempoTracker.phase,
            bpmConfidence = tempoTracker.confidence,
            chroma        = keyTracker.chroma.copyOf(),
            chromaPeak    = keyTracker.chromaPeak,
            keyRoot       = lockedKey?.first,
            keyIsMajor    = lockedKey?.second ?: true,
            keyConfidence = keyTracker.confidence,
            keyChanged    = keyChanged,
        )
    }

    fun endStreaming() {
        active = false
        synchronized(this) { fill = 0 }
        bandDetectors.forEach { it.reset() }
        tempoTracker.reset()
        keyTracker.reset()
    }
}
