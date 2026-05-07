package com.example.myfistapp

data class ExportConfig(
    val mode: Mode,
    val audioSource: AudioSource,
    val beatResponseEnabled: Boolean,
    val resolution: ExportResolution,
)

sealed class AudioSource {
    object UseCurrent : AudioSource()
    object PickNew    : AudioSource()
    object Silent     : AudioSource()
}

enum class ExportResolution(val width: Int, val height: Int, val label: String, val bitrate: Int) {
    HD_720P  (1280, 720,  "720p — small file, fast export",             5_000_000),
    FHD_1080P(1920, 1080, "1080p — recommended",                       10_000_000),
    UHD_4K   (3840, 2160, "4K — large file, slow export",              40_000_000),
}
