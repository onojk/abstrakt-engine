package com.onojk.abstrakt.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.*

internal const val FFT_SIZE = 2048
internal const val FFT_BINS = FFT_SIZE / 2 + 1   // 1025

// 8 logarithmic bands: [lowHz, highHz)
internal val BAND_EDGES_HZ = arrayOf(
    60f   to 120f,
    120f  to 250f,
    250f  to 500f,
    500f  to 1000f,
    1000f to 2000f,
    2000f to 4000f,
    4000f to 8000f,
    8000f to 16000f,
)

internal fun hzToBin(hz: Float, sampleRate: Int): Int =
    (hz * FFT_SIZE / sampleRate).toInt().coerceIn(0, FFT_BINS - 1)

internal fun spectrumMagnitudes(fftBuf: FloatArray): FloatArray {
    val spec = FloatArray(FFT_BINS)
    spec[0] = abs(fftBuf[0])
    for (k in 1 until FFT_SIZE / 2) {
        val re = fftBuf[2 * k]
        val im = fftBuf[2 * k + 1]
        spec[k] = sqrt(re * re + im * im)
    }
    spec[FFT_SIZE / 2] = abs(fftBuf[1])
    return spec
}

internal fun bandEnergies(spectrum: FloatArray, sampleRate: Int): FloatArray =
    FloatArray(8) { b ->
        val (loHz, hiHz) = BAND_EDGES_HZ[b]
        val loBin = hzToBin(loHz, sampleRate)
        val hiBin = (hzToBin(hiHz, sampleRate) + 1).coerceAtMost(FFT_BINS)
        if (loBin >= hiBin) return@FloatArray 0f
        var sum = 0f
        for (k in loBin until hiBin) sum += spectrum[k]
        sum / (hiBin - loBin)
    }

data class AudioFile(
    val uri: Uri,
    val durationMs: Long,
    val sampleRate: Int,
    val channelCount: Int,
    val rmsEnvelope: FloatArray,           // normalized RMS per ~50ms window [numWindows]
    val smoothedBands: Array<FloatArray>,  // EMA-smoothed 8-band values [numWindows][8], 0..1
    val beatFlags: BooleanArray,           // pre-computed beat per window [numWindows]
    val beatLow: FloatArray,               // BeatEnvelope level for Low band (60-250 Hz)  [numWindows]
    val beatMid: FloatArray,               // BeatEnvelope level for Mid band (250-2k Hz)  [numWindows]
    val beatHigh: FloatArray,              // BeatEnvelope level for High band (2k-16k Hz) [numWindows]
    val beatBroadband: FloatArray,         // BeatEnvelope level for Broadband             [numWindows]
)

suspend fun loadAndAnalyze(context: Context, uri: Uri): AudioFile = withContext(Dispatchers.IO) {
    val extractor = MediaExtractor()
    extractor.setDataSource(context, uri, null)

    val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
        extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
    } ?: error("No audio track found in file")

    extractor.selectTrack(trackIndex)
    val format = extractor.getTrackFormat(trackIndex)

    val mime        = format.getString(MediaFormat.KEY_MIME)!!
    val sampleRate  = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
        format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
    val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
    val durationMs  = if (format.containsKey(MediaFormat.KEY_DURATION))
        format.getLong(MediaFormat.KEY_DURATION) / 1000L else 0L

    val codec = MediaCodec.createDecoderByType(mime)
    codec.configure(format, null, null, 0)
    codec.start()

    // ~50ms window
    val windowMonoFrames = (sampleRate * 0.05).toInt().coerceAtLeast(1)
    val windowSamples    = windowMonoFrames * channelCount   // interleaved PCM16 shorts
    val windowBytes      = windowSamples * 2

    val rmsRaw       = mutableListOf<Float>()
    val rawBandsList = mutableListOf<FloatArray>()
    val fluxList     = mutableListOf<Float>()

    // Per-band onset detection — historySize=43 ≈ 6 s at one 50ms window per entry
    val beatDets      = createBandDetectors(sampleRate, historySize = 43)
    val beatLowList   = mutableListOf<Float>()
    val beatMidList   = mutableListOf<Float>()
    val beatHighList  = mutableListOf<Float>()
    val beatBroadList = mutableListOf<Float>()
    val windowDtSec   = windowMonoFrames.toFloat() / sampleRate
    var windowTimeSec = 0f

    val windowBuf  = ByteArray(windowBytes)
    var windowPos  = 0
    var prevSpec   = FloatArray(FFT_BINS)

    val fftEngine  = FloatFFT_1D(FFT_SIZE.toLong())
    val fftBuf     = FloatArray(FFT_SIZE)

    val bufInfo    = MediaCodec.BufferInfo()
    var inputDone  = false
    var outputDone = false

    while (!outputDone) {
        if (!inputDone) {
            val idx = codec.dequeueInputBuffer(10_000L)
            if (idx >= 0) {
                val buf = codec.getInputBuffer(idx)!!
                val n = extractor.readSampleData(buf, 0)
                if (n < 0) {
                    codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    codec.queueInputBuffer(idx, 0, n, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }
        }

        when (val outIdx = codec.dequeueOutputBuffer(bufInfo, 10_000L)) {
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED, MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
            else -> if (outIdx >= 0) {
                val outBuf = codec.getOutputBuffer(outIdx)!!
                val pcm = ByteArray(bufInfo.size)
                outBuf.get(pcm)
                codec.releaseOutputBuffer(outIdx, false)

                var offset = 0
                while (offset < pcm.size) {
                    val toCopy = minOf(pcm.size - offset, windowBytes - windowPos)
                    System.arraycopy(pcm, offset, windowBuf, windowPos, toCopy)
                    windowPos += toCopy
                    offset    += toCopy

                    if (windowPos >= windowBytes) {
                        // ── RMS (for peak uniform) ────────────────────────────
                        var sumSq = 0.0
                        repeat(windowSamples) { i ->
                            val lo = windowBuf[i * 2].toInt() and 0xFF
                            val hi = windowBuf[i * 2 + 1].toInt()
                            val s  = (hi shl 8) or lo
                            sumSq += s.toDouble() * s
                        }
                        rmsRaw.add(sqrt(sumSq / windowSamples).toFloat())

                        // ── FFT: mono float extraction + Hann window ──────────
                        fftBuf.fill(0f)
                        val numFFT = minOf(windowMonoFrames, FFT_SIZE)
                        for (j in 0 until numFFT) {
                            var s = 0.0
                            for (ch in 0 until channelCount) {
                                val byteIdx = (j * channelCount + ch) * 2
                                val lo = windowBuf[byteIdx].toInt() and 0xFF
                                val hi = windowBuf[byteIdx + 1].toInt()
                                s += (hi shl 8) or lo
                            }
                            val hann = 0.5 * (1.0 - cos(2.0 * PI * j / (numFFT - 1)))
                            fftBuf[j] = (s / channelCount / 32768.0 * hann).toFloat()
                        }

                        fftEngine.realForward(fftBuf)
                        val spectrum = spectrumMagnitudes(fftBuf)

                        // ── Spectral flux (half-wave rectified) ───────────────
                        var flux = 0f
                        for (k in 0 until FFT_BINS) {
                            val diff = spectrum[k] - prevSpec[k]
                            if (diff > 0f) flux += diff
                        }
                        fluxList.add(flux)

                        // ── Per-band onset detection ───────────────────────────
                        // processFlux reads prevSpec before it is updated below.
                        beatLowList.add(
                            beatDets[BeatBand.Low.ordinal]
                                .processFlux(spectrum, prevSpec, windowTimeSec, windowDtSec)
                        )
                        beatMidList.add(
                            beatDets[BeatBand.Mid.ordinal]
                                .processFlux(spectrum, prevSpec, windowTimeSec, windowDtSec)
                        )
                        beatHighList.add(
                            beatDets[BeatBand.High.ordinal]
                                .processFlux(spectrum, prevSpec, windowTimeSec, windowDtSec)
                        )
                        beatBroadList.add(
                            beatDets[BeatBand.Broadband.ordinal]
                                .processFlux(spectrum, prevSpec, windowTimeSec, windowDtSec)
                        )
                        windowTimeSec += windowDtSec

                        rawBandsList.add(bandEnergies(spectrum, sampleRate))
                        prevSpec  = spectrum
                        windowPos = 0
                    }
                }

                if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)
                    outputDone = true
            }
        }
    }

    codec.stop()
    codec.release()
    extractor.release()

    val numWindows = rmsRaw.size

    // ── Normalize RMS envelope ────────────────────────────────────────────────
    val maxRms = rmsRaw.maxOrNull() ?: 1f
    val rmsEnvelope = if (maxRms > 0f)
        FloatArray(numWindows) { rmsRaw[it] / maxRms }
    else FloatArray(numWindows)

    // ── Normalize raw bands per-band globally, then EMA-smooth ───────────────
    val bandMax = FloatArray(8)
    for (bands in rawBandsList) {
        for (b in 0..7) if (bands[b] > bandMax[b]) bandMax[b] = bands[b]
    }
    for (bands in rawBandsList) {
        for (b in 0..7) if (bandMax[b] > 0f) bands[b] /= bandMax[b]
    }

    val smoothedBands = Array(numWindows) { FloatArray(8) }
    val ema = FloatArray(8)
    for (w in 0 until numWindows) {
        val raw = rawBandsList[w]
        for (b in 0..7) {
            ema[b] = 0.7f * ema[b] + 0.3f * raw[b]
            smoothedBands[w][b] = ema[b]
        }
    }

    // ── Beat detection: rolling-avg flux, 1.5× threshold, 3-window cooldown ──
    val maxFlux  = fluxList.maxOrNull() ?: 1f
    val fluxNorm = if (maxFlux > 0f) FloatArray(numWindows) { fluxList[it] / maxFlux }
                   else FloatArray(numWindows)

    val beatFlags    = BooleanArray(numWindows)
    val fluxHistory  = ArrayDeque<Float>()
    var lastBeatWin  = -10

    for (w in 0 until numWindows) {
        fluxHistory.addLast(fluxNorm[w])
        if (fluxHistory.size > 20) fluxHistory.removeFirst()
        val avg       = fluxHistory.average().toFloat()
        val candidate = fluxNorm[w] > avg * 1.5f && fluxNorm[w] > 0.01f
        val cooldown  = w - lastBeatWin >= 3
        if (candidate && cooldown) {
            beatFlags[w] = true
            lastBeatWin  = w
        }
    }

    AudioFile(
        uri, durationMs, sampleRate, channelCount,
        rmsEnvelope, smoothedBands, beatFlags,
        beatLow      = FloatArray(numWindows) { beatLowList[it] },
        beatMid      = FloatArray(numWindows) { beatMidList[it] },
        beatHigh     = FloatArray(numWindows) { beatHighList[it] },
        beatBroadband = FloatArray(numWindows) { beatBroadList[it] },
    )
}
