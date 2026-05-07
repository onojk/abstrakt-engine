package com.example.myfistapp.audio

// NOTE: "bands" here are time-windowed views of amplitude, not true frequency bands.
// Real FFT analysis is deferred to a later slice. This is sufficient for slice 1
// visual reactivity but won't distinguish bass from treble.

import kotlin.math.abs

data class AudioSnapshot(
    val bands: FloatArray,  // 8 values in [0,1]; see NOTE above
    val peak: Float,
    val isBeat: Boolean,
)

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

    val low  = avg(30)   // very slow — bass proxy
    val mid  = avg(8)    // medium window
    val fast = avg(2)    // short window

    // high: derivative magnitude — rapid amplitude changes proxy treble/transients
    val prev = (center - 3).coerceAtLeast(0)
    val next = (center + 3).coerceAtMost(n - 1)
    val high = abs(env[next] - env[prev]).coerceAtMost(1f)

    val slowAvg = avg(60)
    val isBeat  = env[center] > slowAvg * 1.4f && env[center] > 0.10f

    val bands = FloatArray(8) { i ->
        when (i) {
            0, 1 -> low
            2, 3 -> mid
            4, 5 -> (fast + high * 0.5f).coerceAtMost(1f)
            6, 7 -> high
            else -> 0f
        }
    }

    return AudioSnapshot(bands = bands, peak = env[center], isBeat = isBeat)
}
