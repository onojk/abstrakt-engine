package com.example.myfistapp

enum class FrameShape {
    None, Circle, Square, Rounded, Hexagon, Octagon, Flower, Star
}

enum class ShapeKind {
    Cylinder, Sphere, Cube, Tetrahedron
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
)

fun KaleidoSettings.frameColorRgbaFloats(): FloatArray {
    val argb = frameColorArgb
    val a = ((argb shr 24) and 0xFFL).toFloat() / 255f
    val r = ((argb shr 16) and 0xFFL).toFloat() / 255f
    val g = ((argb shr 8)  and 0xFFL).toFloat() / 255f
    val b = (argb           and 0xFFL).toFloat() / 255f
    return floatArrayOf(r, g, b, a)
}
