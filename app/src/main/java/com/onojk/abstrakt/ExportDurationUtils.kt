package com.onojk.abstrakt

const val DURATION_MIN_SEC = 5
const val DURATION_MAX_SEC = 600

/** Clamp seconds to the allowed export range (5..600). */
fun clampDuration(sec: Int): Int = sec.coerceIn(DURATION_MIN_SEC, DURATION_MAX_SEC)

/** Format seconds as M:SS — e.g. 5 -> "0:05", 90 -> "1:30", 600 -> "10:00". */
fun formatDuration(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

/**
 * Parse "M:SS" or bare-seconds input. Returns null if empty or unparseable.
 * Does NOT clamp — call clampDuration on the result.
 */
fun parseDurationInput(text: String): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    return if (':' in trimmed) {
        val parts = trimmed.split(':')
        if (parts.size != 2) return null
        val m = parts[0].toIntOrNull() ?: return null
        val s = parts[1].toIntOrNull() ?: return null
        if (s < 0 || s > 59) return null
        m * 60 + s
    } else {
        trimmed.toIntOrNull()
    }
}

/**
 * Rough render-time estimate (seconds of wall-clock time) for a no-audio export.
 * Calibrated: 1920×1080 @ 60 fps ≈ 1× real-time on a modern phone.
 */
fun estimateRenderTimeSec(durationSec: Int, res: ExportResolution, fps: Int): Int {
    val pixelRate = res.width.toLong() * res.height * fps
    val baseline  = 1920L * 1080 * 60
    return (durationSec.toLong() * pixelRate / baseline).toInt().coerceAtLeast(1)
}

/** Format render time as "Xs" or "Xm Ys". */
fun formatRenderTime(sec: Int): String = if (sec < 60) "${sec}s" else "${sec / 60}m ${sec % 60}s"
