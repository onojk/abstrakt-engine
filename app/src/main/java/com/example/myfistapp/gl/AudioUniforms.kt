package com.example.myfistapp.gl

import android.opengl.GLES30
import android.util.Log
import com.example.myfistapp.audio.AudioFile
import com.example.myfistapp.audio.AudioSnapshot
import com.example.myfistapp.audio.snapshotAt

private const val TAG = "AbstraktGL"

private val UNIFORM_NAMES = listOf(
    "u_time", "u_resolution", "u_peak", "u_beat", "u_bands", "u_playback_fraction",
)

internal class AudioUniforms {

    // Written on main thread, read on GL thread — @Volatile ensures visibility.
    @Volatile var audioFile: AudioFile? = null
    @Volatile var playbackFraction: Float = 0f
    @Volatile var activePainter: Painter = Painter.HUE_STRIPE

    // Reset to false in onSurfaceCreated so locations are re-logged after context restore.
    internal var uniformsLogged = false

    fun getSnapshot(): AudioSnapshot =
        audioFile?.let { snapshotAt(it, playbackFraction) }
            ?: AudioSnapshot(FloatArray(8), 0f, false)

    fun applyToProgram(program: ShaderProgram, timeSec: Float) {
        val snap = audioFile?.let { snapshotAt(it, playbackFraction) }
            ?: AudioSnapshot(FloatArray(8), 0f, false)

        if (!uniformsLogged) {
            UNIFORM_NAMES.forEach { name ->
                val loc = GLES30.glGetUniformLocation(program.id, name)
                Log.d(TAG, "uniform '$name' → location $loc${if (loc == -1) " (optimized out)" else ""}")
            }
            uniformsLogged = true
        }

        program.setFloat("u_time", timeSec)
        program.setFloat("u_peak", snap.peak)
        program.setFloat("u_beat", if (snap.isBeat) 1f else 0f)
        program.setFloat("u_playback_fraction", playbackFraction)
        program.setFloatArray("u_bands", snap.bands)
    }
}
