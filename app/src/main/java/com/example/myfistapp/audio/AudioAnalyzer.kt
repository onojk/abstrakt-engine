package com.example.myfistapp.audio

import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.*

data class AudioSnapshot(
    val bands: FloatArray,  // 8 values in [0,1]; logarithmic frequency bands (see BAND_EDGES_HZ)
    val peak: Float,
    val isBeat: Boolean,
)

fun snapshotAt(audioFile: AudioFile, playbackFraction: Float): AudioSnapshot {
    val n = audioFile.rmsEnvelope.size
    if (n == 0) return AudioSnapshot(FloatArray(8), 0f, false)
    val w = (playbackFraction * n).toInt().coerceIn(0, n - 1)
    return AudioSnapshot(
        bands  = audioFile.smoothedBands[w].copyOf(),
        peak   = audioFile.rmsEnvelope[w],
        isBeat = audioFile.beatFlags[w],
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

    private val fftEngine  = FloatFFT_1D(FFT_SIZE.toLong())
    private val fftBuf     = FloatArray(FFT_SIZE)
    private var prevSpec   = FloatArray(FFT_BINS)
    private val bandEma    = FloatArray(8)
    private val bandPeak   = FloatArray(8) { 100f }  // AGC: decays toward actual peaks
    private val fluxHist   = ArrayDeque<Float>()
    private var lastBeatAt = -120   // frame counter for cooldown
    private var frameCtr   = 0

    fun beginStreaming(sampleRate: Int, channels: Int) {
        this.sampleRate = sampleRate
        this.channels   = channels
        synchronized(this) { ring.fill(0); cursor = 0; fill = 0 }
        prevSpec.fill(0f)
        bandEma.fill(0f)
        bandPeak.fill(100f)
        fluxHist.clear()
        lastBeatAt = -120
        frameCtr   = 0
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

        // ── Spectral flux ─────────────────────────────────────────────────────
        var flux = 0f
        for (k in 0 until FFT_BINS) {
            val diff = spectrum[k] - prevSpec[k]
            if (diff > 0f) flux += diff
        }
        prevSpec = spectrum

        fluxHist.addLast(flux)
        if (fluxHist.size > 20) fluxHist.removeFirst()
        val avgFlux = fluxHist.average().toFloat()

        frameCtr++
        // Cooldown ≥ 7 frames ≈ 120ms at 60fps
        val isBeat = flux > avgFlux * 1.5f && flux > 0.5f && (frameCtr - lastBeatAt) >= 7
        if (isBeat) lastBeatAt = frameCtr

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

        return AudioSnapshot(bands = resultBands, peak = peak, isBeat = isBeat)
    }

    fun endStreaming() {
        active = false
        synchronized(this) { fill = 0 }
    }
}
