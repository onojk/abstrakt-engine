package com.example.myfistapp.gl

import android.content.Context
import android.opengl.GLSurfaceView
import com.example.myfistapp.audio.AudioFile

class AbstraktGLSurfaceView(context: Context) : GLSurfaceView(context) {

    private val renderer = AbstraktRenderer()

    init {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setAudioFile(file: AudioFile?)      = renderer.setAudioFile(file)
    fun setPlaybackFraction(fraction: Float) = renderer.setPlaybackFraction(fraction)
}
