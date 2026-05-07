package com.example.myfistapp.gl

enum class Painter(val label: String) {
    HUE_STRIPE("Hue"),
    AUDIO_PAINT("Audio"),
    PRINT_HEAD("Print"),
    IMAGE("Image"),
    SKIN("Skin"),
}

internal data class PainterEntry(
    val painter: Painter,
    val vert: String,
    val frag: String,
)

internal fun allPainters(): List<PainterEntry> = listOf(
    PainterEntry(Painter.HUE_STRIPE,  Shaders.PAINTER_VERT, Shaders.PAINTER_HUESTRIPE_FRAG),
    PainterEntry(Painter.AUDIO_PAINT, Shaders.PAINTER_VERT, Shaders.PAINTER_AUDIOPAINT_FRAG),
    PainterEntry(Painter.PRINT_HEAD,  Shaders.PAINTER_VERT, Shaders.PAINTER_PRINTHEAD_FRAG),
    PainterEntry(Painter.IMAGE,       Shaders.PAINTER_VERT, Shaders.PAINTER_IMAGE_FRAG),
    PainterEntry(Painter.SKIN,        Shaders.PAINTER_VERT, Shaders.PAINTER_SKIN_FRAG),
)
