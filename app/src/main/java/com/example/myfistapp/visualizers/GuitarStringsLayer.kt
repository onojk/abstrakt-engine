package com.example.myfistapp.visualizers

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.myfistapp.audio.AudioFile
import com.example.myfistapp.audio.AudioSnapshot
import com.example.myfistapp.audio.snapshotAt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val STRING_COUNT = 6
private const val TRAIL_COUNT  = 4

// Map each string to one of AudioAnalyzer's 8 envelope-derived bands.
private val STRING_BAND  = intArrayOf(0, 2, 3, 4, 5, 7)

// Visual oscillation in rad/s: 8–20 Hz range
private val STRING_FREQ  = floatArrayOf(50.3f, 62.8f, 75.4f, 87.9f, 106.8f, 125.7f)

// Phase offsets so strings don't all swing in unison
private val STRING_PHASE = floatArrayOf(0f, 0.524f, 1.047f, 1.571f, 2.094f, 2.618f)

// Base angles in radians — 6 strings spread from -12° to +13° within the 30° source wedge
// that the kaleidoscope samples (centered on the +x axis at 0°). After the 12-fold fold
// each string becomes 12 mirrored copies radiating from center.
private val STRING_ANGLE = floatArrayOf(
    (-12.0 * PI / 180.0).toFloat(),
    ( -7.0 * PI / 180.0).toFloat(),
    ( -2.0 * PI / 180.0).toFloat(),
    (  3.0 * PI / 180.0).toFloat(),
    (  8.0 * PI / 180.0).toFloat(),
    ( 13.0 * PI / 180.0).toFloat(),
)

private val STRING_COLORS = arrayOf(
    Color(1.00f, 0.15f, 0.15f),  // red
    Color(1.00f, 0.55f, 0.00f),  // orange
    Color(1.00f, 0.95f, 0.00f),  // yellow
    Color(0.10f, 1.00f, 0.10f),  // green
    Color(0.10f, 0.40f, 1.00f),  // blue
    Color(0.70f, 0.10f, 1.00f),  // violet
)

@Composable
fun GuitarStringsLayer(
    audioFile: AudioFile?,
    playbackFraction: Float,
    modifier: Modifier = Modifier,
) {
    val currentAudioFile by rememberUpdatedState(audioFile)
    val currentFraction  by rememberUpdatedState(playbackFraction)

    // Ring buffer: normalized perpendicular displacement in [-1, 1], one row per string.
    val dispHistory = remember { Array(STRING_COUNT) { FloatArray(TRAIL_COUNT) } }

    // frameIndex drives Canvas redraws AND serves as the ring buffer write cursor.
    var frameIndex by remember { mutableIntStateOf(0) }

    // Single scratch path reused for all draw ops — zero per-frame allocation.
    val scratchPath = remember { Path() }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanos ->
                val tSec = nanos / 1_000_000_000f

                val snap = currentAudioFile?.let { snapshotAt(it, currentFraction) }
                    ?: AudioSnapshot(FloatArray(8), 0f, false)

                val head = frameIndex.mod(TRAIL_COUNT)
                for (i in 0 until STRING_COUNT) {
                    val band = snap.bands[STRING_BAND[i]]
                    dispHistory[i][head] = band * sin(tSec * STRING_FREQ[i] + STRING_PHASE[i])
                }
                frameIndex++
            }
        }
    }

    Canvas(modifier = modifier) {
        val frame    = frameIndex
        val w        = size.width
        val h        = size.height
        val cx       = w / 2f
        val cy       = h / 2f
        val minDim   = min(w, h)

        // Strings reach 40% of minDim from center — well inside the 0.5*minDim sample circle.
        val radius     = minDim * 0.40f
        val halfRadius = radius / 2f

        // Maximum perpendicular displacement: 10% of minDim.
        val maxDispPx = minDim * 0.10f

        // Clamp so the quadratic control point (at halfRadius + disp⊥) stays within
        // r = 0.45*minDim from center: sqrt(halfRadius² + disp²) ≤ 0.45*minDim
        val clampPx = sqrt((0.45f * minDim) * (0.45f * minDim) - halfRadius * halfRadius)
            .coerceAtLeast(1f)

        val strokePx = 2.dp.toPx()

        for (i in 0 until STRING_COUNT) {
            val θ    = STRING_ANGLE[i]
            val cosθ = cos(θ)
            val sinθ = sin(θ)

            // String endpoint (at full radius from center along θ)
            val ex = cx + cosθ * radius
            val ey = cy + sinθ * radius

            val color = STRING_COLORS[i]

            // Draw oldest trail first so newest (fully opaque) sits on top.
            for (j in TRAIL_COUNT - 1 downTo 0) {
                val idx      = (frame - 1 - j).mod(TRAIL_COUNT)
                val rawDisp  = dispHistory[i][idx] * maxDispPx
                val disp     = rawDisp.coerceIn(-clampPx, clampPx)
                // Quadratic falloff: j=0→1.0, j=1→0.56, j=2→0.25, j=3→0.06 — trails vanish fast
                val t     = 1f - j.toFloat() / TRAIL_COUNT
                val alpha = t * t

                // Quadratic control point: halfway along the string, displaced in the
                // direction perpendicular to θ. Perpendicular to (cosθ, sinθ) is (-sinθ, cosθ).
                val cpx = cx + cosθ * halfRadius + (-sinθ) * disp
                val cpy = cy + sinθ * halfRadius +   cosθ  * disp

                scratchPath.reset()
                scratchPath.moveTo(cx, cy)
                scratchPath.quadraticTo(cpx, cpy, ex, ey)

                drawPath(
                    path  = scratchPath,
                    color = color.copy(alpha = alpha),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
    }
}
