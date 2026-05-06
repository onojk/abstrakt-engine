package com.example.myfistapp.gl

enum class Painter(val label: String) {
    HUE_STRIPE("Hue"),
    AUDIO_PAINT("Audio"),
}

internal data class PainterEntry(
    val painter: Painter,
    val vert: String,
    val frag: String,
)

internal fun allPainters(): List<PainterEntry> = listOf(
    PainterEntry(Painter.HUE_STRIPE,  Shaders.PAINTER_VERT, Shaders.PAINTER_HUESTRIPE_FRAG),
    PainterEntry(Painter.AUDIO_PAINT, Shaders.PAINTER_VERT, Shaders.PAINTER_AUDIOPAINT_FRAG),
)
