package com.onojk.abstrakt

import com.onojk.abstrakt.audio.BeatBand
import com.onojk.abstrakt.color.ColorHarmony
import com.onojk.abstrakt.gl.AbstraktGLSurfaceView
import com.onojk.abstrakt.gl.AbstraktRenderer
import com.onojk.abstrakt.PaletteMode
import com.onojk.abstrakt.skin.BuiltinLuts
import java.util.Random

/**
 * Beat-driven random parameter automation.
 * On each beat, fires with probability = intensity and applies a random parameter change.
 * Does NOT write to DataStore — saved settings are unaffected.
 */
class PartyEngine(
    private val applyChange: (Random) -> Unit,
    private val onWarp: () -> Unit = {},
) {
    @Volatile var enabled: Boolean = false
    @Volatile var intensity: Float = 0.5f

    private val random = Random()

    fun onBeat(band: BeatBand = BeatBand.Broadband) {
        if (!enabled) return
        if (random.nextFloat() > intensity) return
        applyChange(random)
        if (random.nextFloat() < intensity * 0.15f) onWarp()
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
    applySingleParamToView(view, available[random.nextInt(available.size)], random)
}

/**
 * Apply ALL unlocked parameters to the live GL view in one pass — the "Surprise me" action.
 * BEAT_REACTIVITY is intentionally a no-op (never randomized), same as the single-param path.
 */
fun applyAllRandomChangesToView(
    view: AbstraktGLSurfaceView,
    lockedParamsFn: () -> Set<LockableParam>,
    random: Random,
) {
    val locked = lockedParamsFn()
    for (param in LockableParam.entries) {
        if (param in locked) continue
        applySingleParamToView(view, param, random)
    }
}

private fun applySingleParamToView(view: AbstraktGLSurfaceView, param: LockableParam, random: Random) {
    when (param) {
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
        LockableParam.CHROMA_ABERRATION_ENABLED -> {
            val enable = random.nextDouble() < 0.3
            view.setChromaAberrationEnabled(enable)
            if (enable) view.setChromaAberrationIntensity(0.004f + random.nextFloat() * 0.011f)
            view.setChromaAberrationAudioReact(enable)
        }
        LockableParam.PALETTE_MODE     -> view.setPaletteMode(PaletteMode.entries.random())
        LockableParam.PALETTE_TINT     -> view.setPaletteTint(random.nextFloat())
        LockableParam.PALETTE_MONO_HUE -> view.setPaletteMonoHue(random.nextFloat() * 360f)
        LockableParam.PALETTE_HARMONY_TYPE    -> view.setHarmonyType(ColorHarmony.entries.random())
        LockableParam.PALETTE_HARMONY_ANCHOR  -> view.setHarmonyAnchorHue(random.nextFloat() * 360f)
        LockableParam.PALETTE_HARMONY_STRENGTH -> view.setHarmonyStrength(0.3f + random.nextFloat() * 0.7f)
        LockableParam.BLACKHOLE_ENABLED    -> view.setBlackholeEnabled(random.nextDouble() < 0.25)
        LockableParam.BLACKHOLE_STRENGTH   -> view.setBlackholeStrength(0.4f + random.nextFloat() * 0.58f)
        LockableParam.BLACKHOLE_SHRINK_RATE -> view.setBlackholeShrinkRate(0.90f + random.nextFloat() * 0.099f)
        LockableParam.BLACKHOLE_ALPHA_RADIUS -> view.setBlackholeAlphaRadius(0.1f + random.nextFloat() * 0.8f)
        LockableParam.BLACKHOLE_WANDER     -> view.setBlackholeWanderAmount(random.nextFloat() * 0.02f)
        LockableParam.LUT_SELECTION -> {
            val entry = BuiltinLuts.all.random()
            view.setLut(BuiltinLuts.generate(entry.id))
            view.setLutEnabled(true)
        }
        LockableParam.LUT_STRENGTH -> view.setLutStrength(0.3f + random.nextFloat() * 0.7f)
        LockableParam.SUDDEN_WARP  -> view.triggerSuddenWarp()
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
    applySingleParamToRenderer(renderer, available[random.nextInt(available.size)], random)
}

private fun applySingleParamToRenderer(renderer: AbstraktRenderer, param: LockableParam, random: Random) {
    when (param) {
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
        LockableParam.CHROMA_ABERRATION_ENABLED -> {
            val enable = random.nextDouble() < 0.3
            renderer.chromaAberrationEnabled = enable
            if (enable) renderer.chromaAberrationIntensity = 0.004f + random.nextFloat() * 0.011f
            renderer.chromaAberrationAudioReact = enable
        }
        LockableParam.PALETTE_MODE     -> renderer.paletteMode    = PaletteMode.entries.random()
        LockableParam.PALETTE_TINT     -> renderer.paletteTint    = random.nextFloat()
        LockableParam.PALETTE_MONO_HUE -> renderer.paletteMonoHue = random.nextFloat() * 360f
        LockableParam.PALETTE_HARMONY_TYPE    -> renderer.harmonyType = ColorHarmony.entries.random()
        LockableParam.PALETTE_HARMONY_ANCHOR  -> renderer.harmonyAnchorHue = random.nextFloat() * 360f
        LockableParam.PALETTE_HARMONY_STRENGTH -> renderer.harmonyStrength = 0.3f + random.nextFloat() * 0.7f
        LockableParam.BLACKHOLE_ENABLED    -> renderer.blackholeEnabled = random.nextDouble() < 0.25
        LockableParam.BLACKHOLE_STRENGTH   -> renderer.blackholeStrength = 0.4f + random.nextFloat() * 0.58f
        LockableParam.BLACKHOLE_SHRINK_RATE -> renderer.blackholeShrinkRate = 0.90f + random.nextFloat() * 0.099f
        LockableParam.BLACKHOLE_ALPHA_RADIUS -> renderer.blackholeAlphaRadius = 0.1f + random.nextFloat() * 0.8f
        LockableParam.BLACKHOLE_WANDER     -> renderer.blackholeWanderAmount = random.nextFloat() * 0.02f
        LockableParam.LUT_SELECTION -> { /* LUT changes go through queueEvent; skip in renderer path */ }
        LockableParam.LUT_STRENGTH -> renderer.lutStrength = 0.3f + random.nextFloat() * 0.7f
        LockableParam.SUDDEN_WARP  -> renderer.triggerSuddenWarp()
    }
}
