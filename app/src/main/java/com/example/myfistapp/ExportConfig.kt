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
    val invertColors: Boolean = false,
    val colorizeEnabled: Boolean = false,
    val colorizeHue: Float = 0f,
    val distortionEnabled: Boolean = false,
    val distortionAmplitude: Float = 0.3f,
    val distortionFrequency: Float = 2.0f,
    val partyEnabled: Boolean = false,
    val randomEnabled: Boolean = false,
    val partyIntensity: Float = 0.5f,
    val contrast: Float = 1.0f,
    val contrastPasses: Int = 1,
    val saturation: Float = 1.0f,
    val distortionPlusEnabled: Boolean = false,
    val distortionPlusYaw: Float = 0f,
    val distortionPlusPitch: Float = 0f,
    val distortionPlusRoll: Float = 0f,
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
