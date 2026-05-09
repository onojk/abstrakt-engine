package com.example.myfistapp

import com.example.myfistapp.gl.AbstraktGLSurfaceView
import com.example.myfistapp.gl.AbstraktRenderer
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

    fun onBeat() {
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

private val PARAM_COUNT = 11

/**
 * Apply one random parameter change to the live GL view (thread-safe via queueEvent/Volatile).
 */
fun applyRandomChangeToView(view: AbstraktGLSurfaceView, random: Random) {
    when (random.nextInt(PARAM_COUNT)) {
        0  -> view.setShapeKind(ShapeKind.entries.random())
        1  -> view.setFoldCount(random.nextInt(23) + 2)
        2  -> view.setFrameShape(FrameShape.entries.random())
        3  -> view.setFrameColorArgb(randomFrameColorArgb(random))
        4  -> view.setInvertColors(random.nextBoolean())
        5  -> view.setColorizeEnabled(random.nextDouble() < 0.7)
        6  -> view.setColorizeHue(random.nextFloat() * 360f)
        7  -> view.setDistortionEnabled(random.nextDouble() < 0.6)
        8  -> view.setDistortionAmplitude(random.nextFloat())
        9  -> view.setDistortionFrequency(0.5f + random.nextFloat() * 7.5f)
        10 -> view.setZoomMultiplier(0.5f + random.nextFloat() * 1.0f)
    }
}

/**
 * Apply one random parameter change directly to the export renderer.
 * Safe to call on the export's single-threaded dispatcher since all fields are @Volatile.
 */
internal fun applyRandomChangeToRenderer(renderer: AbstraktRenderer, random: Random) {
    when (random.nextInt(PARAM_COUNT)) {
        0  -> renderer.setShapeKind(ShapeKind.entries.random())
        1  -> renderer.foldCount = random.nextInt(23) + 2
        2  -> renderer.frameShape = FrameShape.entries.random()
        3  -> renderer.frameColorArgb = randomFrameColorArgb(random)
        4  -> renderer.invertColors = random.nextBoolean()
        5  -> renderer.colorizeEnabled = random.nextDouble() < 0.7
        6  -> renderer.colorizeHue = random.nextFloat() * 360f
        7  -> renderer.distortionEnabled = random.nextDouble() < 0.6
        8  -> renderer.distortionAmplitude = random.nextFloat()
        9  -> renderer.distortionFrequency = 0.5f + random.nextFloat() * 7.5f
        10 -> renderer.zoomMultiplier = 0.5f + random.nextFloat() * 1.0f
    }
}
