package com.example.myfistapp

data class ExportConfig(
    val mode: Mode,
    val audioSource: AudioSource,
    val beatResponseEnabled: Boolean,
    val resolution: ExportResolution,
    val kaleidoFoldCount: Int = 12,
    val kaleidoSquareRotationLocked: Boolean = false,
    val kaleidoFrameShape: FrameShape = FrameShape.Circle,
    val kaleidoFrameColorArgb: Long = 0xFFFFFFFFL,
    val kaleidoShapeKind: ShapeKind = ShapeKind.Cylinder,
)

sealed class AudioSource {
    object UseCurrent : AudioSource()
    object PickNew    : AudioSource()
    object Silent     : AudioSource()
}

enum class ExportResolution(val width: Int, val height: Int, val label: String, val bitrate: Int) {
    HD_720P  (1280, 720,  "720p — small file, fast export",             8_000_000),
    FHD_1080P(1920, 1080, "1080p — recommended",                       16_000_000),
    UHD_4K   (3840, 2160, "4K — large file, slow export",              80_000_000),
}
