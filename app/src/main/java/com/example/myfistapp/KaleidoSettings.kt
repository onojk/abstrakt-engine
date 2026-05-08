package com.example.myfistapp

enum class FrameShape {
    None, Circle, Square, Rounded, Hexagon, Octagon, Star
}

data class KaleidoSettings(
    val foldCount: Int = 12,
    val squareRotationLocked: Boolean = false,
    val frameShape: FrameShape = FrameShape.Circle,
    val frameColorArgb: Long = 0xFFFFFFFFL,
)

fun KaleidoSettings.frameColorRgbaFloats(): FloatArray {
    val argb = frameColorArgb
    val a = ((argb shr 24) and 0xFFL).toFloat() / 255f
    val r = ((argb shr 16) and 0xFFL).toFloat() / 255f
    val g = ((argb shr 8)  and 0xFFL).toFloat() / 255f
    val b = (argb           and 0xFFL).toFloat() / 255f
    return floatArrayOf(r, g, b, a)
}
