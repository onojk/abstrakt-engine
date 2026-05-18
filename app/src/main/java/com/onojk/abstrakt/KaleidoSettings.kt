package com.onojk.abstrakt

enum class LockableParam {
    SHAPE_KIND,
    FOLD_COUNT,
    FRAME_SHAPE,
    FRAME_COLOR,
    INVERT_COLORS,
    COLORIZE_ENABLED,
    COLORIZE_HUE,
    DISTORTION_ENABLED,
    DISTORTION_AMPLITUDE,
    DISTORTION_FREQUENCY,
    ZOOM_MULTIPLIER,
    BASS_ZOOM_INTENSITY,
    CONTRAST,
    CONTRAST_PASSES,
    SATURATION,
    DISTORTION_PLUS_ENABLED,
    DISTORTION_PLUS_YAW,
    DISTORTION_PLUS_PITCH,
    DISTORTION_PLUS_ROLL,
    BEAT_REACTIVITY,
    CHROMA_ABERRATION_ENABLED,
}

enum class FrameShape {
    None, Circle, Square, Rounded, Hexagon, Octagon, Flower, Star
}

enum class ShapeKind {
    Cylinder, Sphere, Cube, Tetrahedron, Icosahedron, Urchin, Caltrop
}

data class KaleidoSettings(
    val foldCount: Int = 12,
    val squareRotationLocked: Boolean = false,
    val frameShape: FrameShape = FrameShape.Circle,
    val frameColorArgb: Long = 0xFFFFFFFFL,
    val zoomMultiplier: Float = 1.0f,
    val shapeKind: ShapeKind = ShapeKind.Cylinder,
    val invertColors: Boolean = false,
    val colorizeEnabled: Boolean = false,
    val colorizeHue: Float = 0f,        // degrees 0..360
    val distortionEnabled: Boolean = false,
    val distortionAmplitude: Float = 0.3f,  // 0..1
    val distortionFrequency: Float = 2.0f,  // 0.5..8.0
    val partyEnabled: Boolean = false,
    val randomEnabled: Boolean = false,
    val partyIntensity: Float = 0.5f,       // shared by both engines; 0..1
    val reactiveEnabled: Boolean = false,
    val reactiveIntensity: Float = 0.5f,
    val bassZoomIntensity: Float = 0.5f,    // 0=off, 1=max bass-driven zoom pulse
    val contrast: Float = 1.0f,            // 0..2, 1=passthrough
    val contrastPasses: Int = 1,           // 1..6, >1=posterization
    val saturation: Float = 1.0f,          // 0..2, 1=passthrough
    val distortionPlusEnabled: Boolean = false,
    val distortionPlusYaw: Float = 0f,     // -180..180 degrees
    val distortionPlusPitch: Float = 0f,   // -90..90 degrees
    val distortionPlusRoll: Float = 0f,    // -180..180 degrees
    val beatReactivity: Float = 0.25f,       // 0=silent, 1=full; scales all beat-driven magnitudes
    val lockedParams: Set<LockableParam> = emptySet(),
    val pitchToHue: Boolean = false,         // drive colorizeHue from chromaPeak * 30° via EMA glide
    val keyChangePartyTrigger: Boolean = false, // fire partyEngine on key transitions (0.75 conf, 2 s cooldown)
    val bpmRotationLock: Boolean = false,    // lock shape rotation speed to detected BPM
    val beatsPerRevolution: Int = 8,         // 1/2/3/4/6/8/16 — how many beats per full revolution
    val chromaAberrationEnabled: Boolean = false,
    val chromaAberrationIntensity: Float = 0.008f,  // 0..0.02 UV offset per channel
    val chromaAberrationAudioReact: Boolean = false, // scale offset by beatDecay * 0.5 on beats
)

fun KaleidoSettings.frameColorRgbaFloats(): FloatArray {
    val argb = frameColorArgb
    val a = ((argb shr 24) and 0xFFL).toFloat() / 255f
    val r = ((argb shr 16) and 0xFFL).toFloat() / 255f
    val g = ((argb shr 8)  and 0xFFL).toFloat() / 255f
    val b = (argb           and 0xFFL).toFloat() / 255f
    return floatArrayOf(r, g, b, a)
}
