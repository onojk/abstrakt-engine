package com.example.myfistapp.gl

import android.content.Context
import android.opengl.GLSurfaceView
import com.example.myfistapp.Mode
import com.example.myfistapp.audio.AudioSnapshot

class AbstraktGLSurfaceView(context: Context) : GLSurfaceView(context) {

    private val renderer = AbstraktRenderer(context)

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setAudioFile(file: com.example.myfistapp.audio.AudioFile?) = renderer.setAudioFile(file)
    fun setPlaybackFraction(fraction: Float) = renderer.setPlaybackFraction(fraction)
    fun setGlMode(mode: GlVizMode)           { renderer.glMode = mode }
    fun setPainter(painter: Painter)         { renderer.audioUniforms.activePainter = painter }
    fun setSkinIndex(index: Int)             { renderer.skinIndex = index }
    fun setKaleidoFolds(folds: Float)        { renderer.kaleidoFolds = folds }
    fun setFoldCount(count: Int)             { renderer.foldCount = count }
    fun setRibbonColor(r: Float, g: Float, b: Float) {
        renderer.ribbonColor[0] = r
        renderer.ribbonColor[1] = g
        renderer.ribbonColor[2] = b
    }
    fun setBeatThreshold(threshold: Float)   { renderer.beatThreshold = threshold }
    fun setUserSkinFile(path: String?)       { renderer.userSkinFilePath = path }
    fun setCurrentMode(mode: Mode)              { renderer.currentMode = mode }
    fun invalidateUserSkinTexture(path: String) { queueEvent { renderer.invalidateUserSkinTexture(path) } }
    fun setRendererReadyCallback(cb: () -> Unit) { renderer.onReadyCallback = cb }
    fun setLiveSnapshot(snap: AudioSnapshot?)    { renderer.audioUniforms.liveSnapshot = snap }
}
