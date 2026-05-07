package com.example.myfistapp.audio

// NOTE: "bands" here are time-windowed views of amplitude, not true frequency bands.
// Real FFT analysis is deferred to a later slice. This is sufficient for slice 1
// visual reactivity but won't distinguish bass from treble.

import kotlin.math.abs
import kotlin.math.sqrt

data class AudioSnapshot(
    val bands: FloatArray,  // 8 values in [0,1]; see NOTE above
    val peak: Float,
    val isBeat: Boolean,
)

private fun computeSnapshot(
    low: Float, mid: Float, fast: Float, high: Float,
    current: Float, slowAvg: Float,
): AudioSnapshot {
    val isBeat = current > slowAvg * 1.4f && current > 0.10f
    val bands = FloatArray(8) { i ->
        when (i) {
            0, 1 -> low
            2, 3 -> mid
            4, 5 -> (fast + high * 0.5f).coerceAtMost(1f)
            6, 7 -> high
            else -> 0f
        }
    }
    return AudioSnapshot(bands = bands, peak = current, isBeat = isBeat)
}

fun snapshotAt(audioFile: AudioFile, playbackFraction: Float): AudioSnapshot {
    val env = audioFile.amplitudeEnvelope
    if (env.isEmpty()) return AudioSnapshot(FloatArray(8), 0f, false)

    val n = env.size
    val center = (playbackFraction * n).toInt().coerceIn(0, n - 1)

    fun avg(half: Int): Float {
        val lo = (center - half).coerceAtLeast(0)
        val hi = (center + half).coerceAtMost(n - 1)
        var s = 0f
        for (i in lo..hi) s += env[i]
        return s / (hi - lo + 1)
    }

    val low     = avg(30)
    val mid     = avg(8)
    val fast    = avg(2)
    val prev    = (center - 3).coerceAtLeast(0)
    val next    = (center + 3).coerceAtMost(n - 1)
    val high    = abs(env[next] - env[prev]).coerceAtMost(1f)
    val slowAvg = avg(60)

    return computeSnapshot(low, mid, fast, high, env[center], slowAvg)
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

    fun beginStreaming(sampleRate: Int, channels: Int) {
        this.sampleRate = sampleRate
        this.channels   = channels
        synchronized(this) { ring.fill(0); cursor = 0; fill = 0 }
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
        val (ringSnap, curSnap, fillSnap) = synchronized(this) {
            if (fill == 0) return AudioSnapshot(FloatArray(8), 0f, false)
            Triple(ring.copyOf(), cursor, fill)
        }

        val f    = maxOf(sampleRate / 60, 1)
        val base = curSnap + ringSize * 2  // offset keeps modulo non-negative

        fun rms(fromEnd: Int, count: Int): Float {
            val n = minOf(count, (fillSnap - fromEnd).coerceAtLeast(0))
            if (n == 0) return 0f
            var sum = 0.0
            repeat(n) { i ->
                val s = ringSnap[(base - 1 - fromEnd - i) % ringSize] / 32768.0
                sum += s * s
            }
            return sqrt(sum / n).toFloat().coerceIn(0f, 1f)
        }

        val curr    = rms(0, f)
        val fast    = rms(0, f * 2)
        val mid     = rms(0, f * 8)
        val slow    = rms(0, f * 30)
        val older   = rms(f * 3, f)
        val high    = abs(curr - older).coerceAtMost(1f)
        val slowAvg = rms(0, f * 60)

        return computeSnapshot(slow, mid, fast, high, curr, slowAvg)
    }

    fun endStreaming() {
        active = false
        synchronized(this) { fill = 0 }
    }
}
