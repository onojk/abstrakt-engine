package com.onojk.abstrakt.gl

import android.content.Context
import android.os.Build
import android.opengl.GLSurfaceView
import android.view.Surface
import com.onojk.abstrakt.FrameShape
import com.onojk.abstrakt.Mode
import com.onojk.abstrakt.PaletteMode
import com.onojk.abstrakt.ShapeKind
import com.onojk.abstrakt.color.ColorHarmony
import com.onojk.abstrakt.audio.AudioSnapshot
import com.onojk.abstrakt.skin.CubeLut
import kotlinx.coroutines.flow.StateFlow

class AbstraktGLSurfaceView(context: Context) : GLSurfaceView(context) {

    private val renderer = AbstraktRenderer(context)

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true

        // Tell SurfaceFlinger this surface wants 120Hz so the adaptive display
        // scheduler doesn't downclock the panel to 60Hz during idle, which
        // causes the compositor to drop every other rendered frame → stutter.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            holder.addCallback(object : android.view.SurfaceHolder.Callback {
                override fun surfaceCreated(h: android.view.SurfaceHolder) {
                    h.surface.setFrameRate(
                        120f,
                        Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                    )
                }
                override fun surfaceChanged(h: android.view.SurfaceHolder, f: Int, w: Int, h2: Int) {}
                override fun surfaceDestroyed(h: android.view.SurfaceHolder) {}
            })
        }
    }

    fun setAudioFile(file: com.onojk.abstrakt.audio.AudioFile?) = renderer.setAudioFile(file)
    fun setPlaybackFraction(fraction: Float) = renderer.setPlaybackFraction(fraction)
    fun setGlMode(mode: GlVizMode)           { renderer.glMode = mode }
    fun setPainter(painter: Painter)         { renderer.audioUniforms.activePainter = painter }
    fun setSkinIndex(index: Int)             { renderer.skinIndex = index }
    fun setKaleidoFolds(folds: Float)        { renderer.kaleidoFolds = folds }
    fun setFoldCount(count: Int)              { renderer.foldCount = count }
    fun setSquareRotationLocked(value: Boolean) { renderer.squareRotationLocked = value }
    fun setFrameShape(value: FrameShape)         { renderer.frameShape = value }
    fun setFrameColorArgb(value: Long)           { renderer.frameColorArgb = value }
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
    fun triggerPainterStatsDump()                { renderer.dumpPainterStatsOnNextFrame = true }
    // SHAKEDIAG — force renderer to see a frozen silent snapshot every frame (isolation test).
    fun setDebugForceSilent(v: Boolean)          { renderer.audioUniforms.debugForceSilentSnapshot = v }
    fun setShapeKind(kind: ShapeKind)            { queueEvent { renderer.setShapeKind(kind) } }
    fun setInvertColors(value: Boolean)          { queueEvent { renderer.invertColors = value } }
    fun setColorizeEnabled(value: Boolean)        { queueEvent { renderer.colorizeEnabled = value } }
    fun setColorizeHue(value: Float)              { queueEvent { renderer.colorizeHue = value } }
    fun setDistortionEnabled(value: Boolean)      { queueEvent { renderer.distortionEnabled = value } }
    fun setDistortionAmplitude(value: Float)      { queueEvent { renderer.distortionAmplitude = value } }
    fun setDistortionFrequency(value: Float)      { queueEvent { renderer.distortionFrequency = value } }
    fun setZoomMultiplier(value: Float)          { queueEvent { renderer.zoomMultiplier = value } }
    fun setBassZoomIntensity(value: Float)       { queueEvent { renderer.bassZoomIntensity = value } }
    fun setContrast(value: Float)               { queueEvent { renderer.contrast = value } }
    fun setContrastPasses(value: Int)           { queueEvent { renderer.contrastPasses = value } }
    fun setSaturation(value: Float)             { queueEvent { renderer.saturation = value } }
    fun setDistortionPlusEnabled(value: Boolean) { queueEvent { renderer.distortionPlusEnabled = value } }
    fun setDistortionPlusYaw(value: Float)       { queueEvent { renderer.distortionPlusYaw = value } }
    fun setDistortionPlusPitch(value: Float)     { queueEvent { renderer.distortionPlusPitch = value } }
    fun setDistortionPlusRoll(value: Float)      { queueEvent { renderer.distortionPlusRoll = value } }
    fun setBeatReactivity(value: Float)              { queueEvent { renderer.beatReactivity = value } }
    fun setRotationSpeedTarget(radPerSec: Float?)    { renderer.rotationSpeedTarget = radPerSec }
    fun setChromaAberrationEnabled(value: Boolean)   { queueEvent { renderer.chromaAberrationEnabled = value } }
    fun setChromaAberrationIntensity(value: Float)   { queueEvent { renderer.chromaAberrationIntensity = value } }
    fun setChromaAberrationAudioReact(value: Boolean){ queueEvent { renderer.chromaAberrationAudioReact = value } }
    fun setBlackholeEnabled(value: Boolean)      { queueEvent { renderer.blackholeEnabled = value } }
    fun setBlackholeStrength(value: Float)       { queueEvent { renderer.blackholeStrength = value } }
    fun setBlackholeShrinkRate(value: Float)     { queueEvent { renderer.blackholeShrinkRate = value } }
    fun setBlackholeAlphaRadius(value: Float)    { queueEvent { renderer.blackholeAlphaRadius = value } }
    fun setBlackholeWanderAmount(value: Float)   { queueEvent { renderer.blackholeWanderAmount = value } }
    fun setPaletteMode(value: PaletteMode)      { renderer.paletteMode = value }
    fun setPaletteTint(value: Float)            { renderer.paletteTint = value }
    fun setPaletteMonoHue(value: Float)         { renderer.paletteMonoHue = value }
    fun setHarmonyType(value: ColorHarmony)     { renderer.harmonyType = value }
    fun setHarmonyAnchorHue(value: Float)       { renderer.harmonyAnchorHue = value }
    fun setHarmonySaturation(value: Float)      { renderer.harmonySaturation = value }
    fun setHarmonyValue(value: Float)           { renderer.harmonyValue = value }
    fun setHarmonyStrength(value: Float)        { renderer.harmonyStrength = value }
    fun cyclePainter()                               { queueEvent { renderer.cyclePainter() } }
    val currentShapeName: StateFlow<String>      get() = renderer.currentShapeName
    fun captureMosaicController(): CaptureMosaicController = renderer.captureController
    fun kaleidoFboWidth(): Int  = renderer.kaleidoTexW
    fun kaleidoFboHeight(): Int = renderer.kaleidoTexH
    fun setLut(lut: CubeLut?)         { queueEvent { renderer.applyLut(lut) } }
    fun setLutEnabled(value: Boolean) { renderer.lutEnabled  = value }
    fun setLutStrength(value: Float)  { renderer.lutStrength = value }
    fun setSuddenWarpEnabled(value: Boolean) { queueEvent { renderer.suddenWarpEnabled = value } }
    fun triggerSuddenWarp()                  { queueEvent { renderer.triggerSuddenWarp() } }
    fun setLightningEnabled(value: Boolean)        { queueEvent { renderer.lightningEnabled        = value } }
    fun setLightningSpritesLimit5s(value: Boolean) { queueEvent { renderer.lightningSpritesLimit5s = value } }
    fun setLightningTouch(uvX: Float, uvY: Float, touching: Boolean) {
        renderer.lightningTouchX   = uvX
        renderer.lightningTouchY   = uvY
        renderer.lightningTouching = touching
    }
}
