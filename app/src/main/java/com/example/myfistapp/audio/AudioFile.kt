package com.example.myfistapp.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

data class AudioFile(
    val uri: Uri,
    val durationMs: Long,
    val sampleRate: Int,
    val channelCount: Int,
    val amplitudeEnvelope: FloatArray,
)

suspend fun loadAndAnalyze(context: Context, uri: Uri): AudioFile = withContext(Dispatchers.IO) {
    val extractor = MediaExtractor()
    extractor.setDataSource(context, uri, null)

    val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
        extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
    } ?: error("No audio track found in file")

    extractor.selectTrack(trackIndex)
    val format = extractor.getTrackFormat(trackIndex)

    val mime = format.getString(MediaFormat.KEY_MIME)!!
    val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
        format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
    val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
    val durationMs = if (format.containsKey(MediaFormat.KEY_DURATION))
        format.getLong(MediaFormat.KEY_DURATION) / 1000L else 0L

    val codec = MediaCodec.createDecoderByType(mime)
    codec.configure(format, null, null, 0)
    codec.start()

    // ~50 ms window: (sampleRate * 0.05) samples per channel × channels × 2 bytes (PCM16 LE)
    val windowSamples = ((sampleRate * 0.05).toInt() * channelCount).coerceAtLeast(1)
    val windowBytes = windowSamples * 2

    val envelope = mutableListOf<Float>()
    val windowBuf = ByteArray(windowBytes)
    var windowPos = 0

    val bufInfo = MediaCodec.BufferInfo()
    var inputDone = false
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
                    offset += toCopy
                    if (windowPos >= windowBytes) {
                        // RMS over window: PCM16-LE, interleaved channels
                        var sumSq = 0.0
                        repeat(windowSamples) { i ->
                            val lo = windowBuf[i * 2].toInt() and 0xFF
                            val hi = windowBuf[i * 2 + 1].toInt() // sign-extends
                            val s = (hi shl 8) or lo
                            sumSq += s.toDouble() * s
                        }
                        envelope.add(sqrt(sumSq / windowSamples).toFloat())
                        windowPos = 0
                    }
                }

                if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            }
        }
    }

    codec.stop()
    codec.release()
    extractor.release()

    val maxVal = envelope.maxOrNull() ?: 1f
    val normalized = if (maxVal > 0f)
        FloatArray(envelope.size) { envelope[it] / maxVal }
    else
        FloatArray(envelope.size)

    AudioFile(uri, durationMs, sampleRate, channelCount, normalized)
}
