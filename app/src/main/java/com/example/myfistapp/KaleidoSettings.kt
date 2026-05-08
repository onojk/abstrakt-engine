package com.example.myfistapp

enum class FrameShape {
    None, Circle, Square, Rounded, Hexagon, Octagon, Star
}

data class KaleidoSettings(
    val foldCount: Int = 12,
    val squareRotationLocked: Boolean = false,
    val frameShape: FrameShape = FrameShape.Circle,
)
