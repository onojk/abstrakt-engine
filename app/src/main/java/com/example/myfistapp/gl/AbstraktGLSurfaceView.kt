package com.example.myfistapp.gl

import android.content.Context
import android.opengl.GLSurfaceView
import com.example.myfistapp.audio.AudioFile

class AbstraktGLSurfaceView(context: Context) : GLSurfaceView(context) {

    private val renderer = AbstraktRenderer(context)

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0) // 16-bit depth buffer for 3D modes
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setAudioFile(file: AudioFile?)       = renderer.setAudioFile(file)
    fun setPlaybackFraction(fraction: Float) = renderer.setPlaybackFraction(fraction)
    fun setGlMode(mode: GlVizMode)           { renderer.glMode = mode }
    fun setPainter(painter: Painter)         { renderer.audioUniforms.activePainter = painter }
}
