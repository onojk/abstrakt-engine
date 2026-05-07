package com.example.myfistapp

import com.example.myfistapp.gl.Painter

data class SkinConfig(
    val painter: Painter,
    val skinIndex: Int,
    val folds: Float,
    val rr: Float, val rg: Float, val rb: Float,
    val beatThreshold: Float,
)

fun skinConfigForMode(mode: Mode): SkinConfig = when (mode) {
    Mode.Cyclone     -> SkinConfig(Painter.IMAGE, 0, 12f, 0f,    0f,    0f,    0.40f)
    is Mode.Builtin  -> when (mode.skinIndex) {
        1            -> SkinConfig(Painter.SKIN,  0, 12f, 0f,    0f,    0f,    0.40f)
        2            -> SkinConfig(Painter.SKIN,  1,  8f, 0.20f, 0.05f, 0.30f, 0.30f)
        3            -> SkinConfig(Painter.SKIN,  2, 16f, 0.95f, 0.95f, 0.95f, 0.50f)
        4            -> SkinConfig(Painter.SKIN,  3,  6f, 0.05f, 0.20f, 0.22f, 0.35f)
        5            -> SkinConfig(Painter.SKIN,  4, 10f, 0.25f, 0.05f, 0.08f, 0.45f)
        else         -> SkinConfig(Painter.IMAGE, 0, 12f, 0f,    0f,    0f,    0.40f)
    }
    is Mode.UserSlot -> SkinConfig(Painter.SKIN, -1, 12f, 0f, 0f, 0f, 0.40f)
    Mode.AddSlot     -> SkinConfig(Painter.IMAGE, 0, 12f, 0f, 0f, 0f, 0.40f)
}
