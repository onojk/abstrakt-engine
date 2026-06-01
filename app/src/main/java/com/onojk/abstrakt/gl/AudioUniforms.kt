package com.onojk.abstrakt.gl

import android.opengl.GLES30
import android.util.Log
import com.onojk.abstrakt.audio.AudioFile
import com.onojk.abstrakt.audio.AudioSnapshot
import com.onojk.abstrakt.audio.snapshotAt

private const val TAG = "AbstraktGL"

private val UNIFORM_NAMES = listOf(
    "u_time", "u_resolution", "u_peak", "u_beat", "u_bands", "u_playback_fraction",
)

internal class AudioUniforms {

    // Written on main thread, read on GL thread — @Volatile ensures visibility.
    @Volatile var audioFile: AudioFile? = null
    @Volatile var playbackFraction: Float = 0f
    @Volatile var activePainter: Painter = Painter.HUE_STRIPE
    // Non-null while mic is active; takes priority over file-based snapshot.
    @Volatile var liveSnapshot: AudioSnapshot? = null
    // SHAKEDIAG isolation toggle — when true, renderer always sees a frozen silent snapshot
    // regardless of mic/file state. Toggle via AbstraktGLSurfaceView.setDebugForceSilent().
    @Volatile var debugForceSilentSnapshot = false

    private val SILENT_SNAP = AudioSnapshot(FloatArray(8), 0f, false)

    // Reset to false in onSurfaceCreated so locations are re-logged after context restore.
    internal var uniformsLogged = false

    fun getSnapshot(): AudioSnapshot {
        if (debugForceSilentSnapshot) return SILENT_SNAP
        return liveSnapshot
            ?: audioFile?.let { snapshotAt(it, playbackFraction) }
            ?: SILENT_SNAP
    }

    fun applyToProgram(program: ShaderProgram, timeSec: Float) {
        val snap = getSnapshot()

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
