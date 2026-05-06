package com.example.myfistapp.gl

import android.opengl.GLES30
import android.util.Log
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val TAG = "AbstraktGL"

private const val INF_COUNT = 5

// Orbit parameters matching WarpfieldView.kt exactly.
private val INF_ORBIT = floatArrayOf(0.20f, 0.26f, 0.17f, 0.23f, 0.21f)
private val INF_SPEED = floatArrayOf(0.10f, 0.07f, 0.13f, 0.06f, 0.11f)
private val INF_PHASE = floatArrayOf(0f, 1.257f, 2.513f, 3.770f, 5.026f)

internal class Influencers(private val audioUniforms: AudioUniforms) {

    // posXY: interleaved (x0,y0, x1,y1, …) — handed directly to glUniform2fv.
    private val posXY    = FloatArray(INF_COUNT * 2)
    private val strength = FloatArray(INF_COUNT)
    private var infRadius = 180f
    private var beatFlash = 0f

    fun updateForFrame(timeSec: Float, dt: Float, resW: Float, resH: Float) {
        val snap = audioUniforms.getSnapshot()

        if (snap.isBeat) beatFlash = 1f
        beatFlash = (beatFlash - dt * 3f).coerceAtLeast(0f)

        val minDim = min(resW, resH)
        infRadius = (0.18f + snap.bands[1] * 0.07f) * minDim
        val baseStrength = 45f + snap.bands[0] * 85f + beatFlash * 65f

        for (k in 0 until INF_COUNT) {
            posXY[k * 2]     = resW / 2f + cos(timeSec * INF_SPEED[k] + INF_PHASE[k]) * resW * INF_ORBIT[k]
            posXY[k * 2 + 1] = resH / 2f + sin(timeSec * INF_SPEED[k] + INF_PHASE[k]) * resH * INF_ORBIT[k]
            strength[k] = baseStrength
        }
    }

    private var locationsLogged = false

    fun applyToProgram(program: ShaderProgram) {
        if (!locationsLogged) {
            listOf("u_influencer_pos", "u_influencer_strength", "u_inf_radius",
                   "u_grid_dim", "u_dot_radius").forEach { name ->
                val loc = GLES30.glGetUniformLocation(program.id, name)
                Log.d(TAG, "warp uniform '$name' → location $loc${if (loc == -1) " (optimized out)" else ""}")
            }
            locationsLogged = true
        }
        program.setVec2Array("u_influencer_pos", posXY)
        program.setFloatArray("u_influencer_strength", strength)
        program.setFloat("u_inf_radius", infRadius)
    }
}
