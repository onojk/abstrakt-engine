package com.onojk.abstrakt

import com.onojk.abstrakt.audio.BeatBand
import com.onojk.abstrakt.gl.AbstraktGLSurfaceView
import com.onojk.abstrakt.gl.AbstraktRenderer
import java.util.Random

/**
 * Beat-driven random parameter automation.
 * On each beat, fires with probability = intensity and applies a random parameter change.
 * Does NOT write to DataStore — saved settings are unaffected.
 */
class PartyEngine(private val applyChange: (Random) -> Unit) {
    @Volatile var enabled: Boolean = false
    @Volatile var intensity: Float = 0.5f

    private val random = Random()

    fun onBeat(band: BeatBand = BeatBand.Broadband) {
        if (!enabled) return
        if (random.nextFloat() > intensity) return
        applyChange(random)
    }
}

// ── Shared randomization helpers ──────────────────────────────────────────────

private fun randomFrameColorArgb(r: Random): Long {
    val h = r.nextFloat() * 360f
    val s = 0.7f + r.nextFloat() * 0.3f
    val v = 0.8f + r.nextFloat() * 0.2f
    val color = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    return (color.toLong() and 0xFFFFFFFFL) or 0xFF000000L
}

/**
 * Apply one random parameter change to the live GL view (thread-safe via queueEvent/Volatile).
 * Skips any parameter whose LockableParam is in the locked set returned by lockedParamsFn.
 */
fun applyRandomChangeToView(
    view: AbstraktGLSurfaceView,
    lockedParamsFn: () -> Set<LockableParam>,
    random: Random,
) {
    val available = LockableParam.entries.filter { it !in lockedParamsFn() }
    if (available.isEmpty()) return
    when (available[random.nextInt(available.size)]) {
        LockableParam.SHAPE_KIND           -> view.setShapeKind(ShapeKind.entries.random())
        LockableParam.FOLD_COUNT           -> view.setFoldCount(random.nextInt(23) + 2)
        LockableParam.FRAME_SHAPE          -> view.setFrameShape(FrameShape.entries.random())
        LockableParam.FRAME_COLOR          -> view.setFrameColorArgb(randomFrameColorArgb(random))
        LockableParam.INVERT_COLORS        -> view.setInvertColors(random.nextBoolean())
        LockableParam.COLORIZE_ENABLED     -> view.setColorizeEnabled(random.nextDouble() < 0.7)
        LockableParam.COLORIZE_HUE         -> view.setColorizeHue(random.nextFloat() * 360f)
        LockableParam.DISTORTION_ENABLED   -> view.setDistortionEnabled(random.nextDouble() < 0.6)
        LockableParam.DISTORTION_AMPLITUDE -> view.setDistortionAmplitude(random.nextFloat())
        LockableParam.DISTORTION_FREQUENCY -> view.setDistortionFrequency(0.5f + random.nextFloat() * 7.5f)
        LockableParam.ZOOM_MULTIPLIER      -> view.setZoomMultiplier(0.5f + random.nextFloat() * 1.0f)
        LockableParam.BASS_ZOOM_INTENSITY  -> view.setBassZoomIntensity(random.nextFloat())
        LockableParam.CONTRAST             -> view.setContrast(0.5f + random.nextFloat() * 1.5f)
        LockableParam.CONTRAST_PASSES      -> view.setContrastPasses(1 + random.nextInt(6))
        LockableParam.SATURATION           -> view.setSaturation(random.nextFloat() * 2f)
        LockableParam.DISTORTION_PLUS_ENABLED -> view.setDistortionPlusEnabled(random.nextDouble() < 0.4)
        LockableParam.DISTORTION_PLUS_YAW  -> view.setDistortionPlusYaw(-180f + random.nextFloat() * 360f)
        LockableParam.DISTORTION_PLUS_PITCH -> view.setDistortionPlusPitch(-45f + random.nextFloat() * 90f)
        LockableParam.DISTORTION_PLUS_ROLL -> view.setDistortionPlusRoll(-180f + random.nextFloat() * 360f)
        LockableParam.BEAT_REACTIVITY      -> { /* Master sensitivity: locked-by-user-only, not randomized. */ }
    }
}

/**
 * Apply one random parameter change directly to the export renderer.
 * Safe to call on the export's single-threaded dispatcher since all fields are @Volatile.
 */
internal fun applyRandomChangeToRenderer(
    renderer: AbstraktRenderer,
    lockedParams: Set<LockableParam>,
    random: Random,
) {
    val available = LockableParam.entries.filter { it !in lockedParams }
    if (available.isEmpty()) return
    when (available[random.nextInt(available.size)]) {
        LockableParam.SHAPE_KIND           -> renderer.setShapeKind(ShapeKind.entries.random())
        LockableParam.FOLD_COUNT           -> renderer.foldCount = random.nextInt(23) + 2
        LockableParam.FRAME_SHAPE          -> renderer.frameShape = FrameShape.entries.random()
        LockableParam.FRAME_COLOR          -> renderer.frameColorArgb = randomFrameColorArgb(random)
        LockableParam.INVERT_COLORS        -> renderer.invertColors = random.nextBoolean()
        LockableParam.COLORIZE_ENABLED     -> renderer.colorizeEnabled = random.nextDouble() < 0.7
        LockableParam.COLORIZE_HUE         -> renderer.colorizeHue = random.nextFloat() * 360f
        LockableParam.DISTORTION_ENABLED   -> renderer.distortionEnabled = random.nextDouble() < 0.6
        LockableParam.DISTORTION_AMPLITUDE -> renderer.distortionAmplitude = random.nextFloat()
        LockableParam.DISTORTION_FREQUENCY -> renderer.distortionFrequency = 0.5f + random.nextFloat() * 7.5f
        LockableParam.ZOOM_MULTIPLIER      -> renderer.zoomMultiplier = 0.5f + random.nextFloat() * 1.0f
        LockableParam.BASS_ZOOM_INTENSITY  -> renderer.bassZoomIntensity = random.nextFloat()
        LockableParam.CONTRAST             -> renderer.contrast = 0.5f + random.nextFloat() * 1.5f
        LockableParam.CONTRAST_PASSES      -> renderer.contrastPasses = 1 + random.nextInt(6)
        LockableParam.SATURATION           -> renderer.saturation = random.nextFloat() * 2f
        LockableParam.DISTORTION_PLUS_ENABLED -> renderer.distortionPlusEnabled = random.nextDouble() < 0.4
        LockableParam.DISTORTION_PLUS_YAW  -> renderer.distortionPlusYaw = -180f + random.nextFloat() * 360f
        LockableParam.DISTORTION_PLUS_PITCH -> renderer.distortionPlusPitch = -45f + random.nextFloat() * 90f
        LockableParam.DISTORTION_PLUS_ROLL -> renderer.distortionPlusRoll = -180f + random.nextFloat() * 360f
        LockableParam.BEAT_REACTIVITY      -> { /* never randomized */ }
    }
}
