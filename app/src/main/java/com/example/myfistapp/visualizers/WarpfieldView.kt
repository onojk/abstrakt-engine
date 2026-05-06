package com.example.myfistapp.visualizers

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.myfistapp.audio.AudioFile
import com.example.myfistapp.audio.AudioSnapshot
import com.example.myfistapp.audio.snapshotAt
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val COLS = 40
private const val ROWS = 70
private const val DOT_COUNT = COLS * ROWS
private const val INF_COUNT = 3
private const val HUE_BUCKETS = 16

@Composable
fun WarpfieldCanvas(
    audioFile: AudioFile?,
    playbackFraction: Float,
    modifier: Modifier = Modifier,
) {
    // Home positions as fractions [0,1] — resolved to pixels inside Canvas
    val homeX = remember { FloatArray(DOT_COUNT) { i -> (i % COLS + 0.5f) / COLS } }
    val homeY = remember { FloatArray(DOT_COUNT) { i -> (i / COLS + 0.5f) / ROWS } }

    // beatFlash is plain mutable (not Compose state) — read in draw, updated in effect
    val beatFlash = remember { FloatArray(1) { 0f } }

    // tick drives Canvas redraws: reading it inside the draw lambda registers an observer
    var tick by remember { mutableLongStateOf(0L) }

    // rememberUpdatedState lets the LaunchedEffect(Unit) see the latest playback position
    val currentFraction by rememberUpdatedState(playbackFraction)
    val currentAudioFile by rememberUpdatedState(audioFile)

    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (lastNanos == 0L) 0.016f
                         else ((nanos - lastNanos) / 1_000_000_000f).coerceAtMost(0.1f)
                lastNanos = nanos

                val snap = currentAudioFile?.let { snapshotAt(it, currentFraction) }
                    ?: AudioSnapshot(FloatArray(8), 0f, false)

                if (snap.isBeat) beatFlash[0] = 1f
                beatFlash[0] = (beatFlash[0] - dt * 3f).coerceAtLeast(0f)

                tick = nanos  // triggers Canvas redraw via state observation
            }
        }
    }

    // Pre-allocated bucket buffers — cleared and refilled each frame, avoids per-frame allocation
    val bucketPts = remember { Array(HUE_BUCKETS) { ArrayList<Offset>(DOT_COUNT / HUE_BUCKETS + 16) } }

    Canvas(modifier = modifier) {
        // Reading tick here registers this draw node as an observer —
        // when tick changes the frame loop invalidates this Canvas for redraw.
        val tSec = tick / 1_000_000_000f

        val w = size.width
        val h = size.height

        val snap = audioFile?.let { snapshotAt(it, playbackFraction) }
            ?: AudioSnapshot(FloatArray(8), 0f, false)

        val bf       = beatFlash[0]
        val infR     = (0.32f + snap.bands[1] * 0.18f) * min(w, h)
        val strength = 55f + snap.bands[0] * 110f + bf * 70f
        val hueBase  = snap.bands[0] * 200f

        // 3 influencers drift on slow time-based circular paths — no extra state needed
        val ix = FloatArray(INF_COUNT) { k ->
            w / 2f + cos(tSec * 0.08f * (1f + k * 0.4f) + k * 2.094f) * w * 0.27f
        }
        val iy = FloatArray(INF_COUNT) { k ->
            h / 2f + sin(tSec * 0.08f * (1f + k * 0.4f) + k * 2.094f) * h * 0.27f
        }

        for (b in 0 until HUE_BUCKETS) bucketPts[b].clear()

        val dotPx = 4.dp.toPx()

        for (i in 0 until DOT_COUNT) {
            val hx = homeX[i] * w
            val hy = homeY[i] * h
            var dx = 0f
            var dy = 0f

            for (k in 0 until INF_COUNT) {
                val ex = hx - ix[k]
                val ey = hy - iy[k]
                val d  = sqrt(ex * ex + ey * ey)
                if (d < infR && d > 0.5f) {
                    val fall  = 1f - d / infR
                    val force = strength * fall * fall
                    // alternating attract / repel per influencer
                    val sign  = if (k % 2 == 0) 1f else -1f
                    dx += sign * (ex / d) * force
                    dy += sign * (ey / d) * force
                }
            }

            val mag     = sqrt(dx * dx + dy * dy)
            val maxDisp = (strength * 0.6f).coerceAtLeast(1f)
            val tColor  = (mag / maxDisp).coerceIn(0f, 1f)
            val hue     = (hueBase + tColor * 160f) % 360f
            val bucket  = ((hue / 360f) * HUE_BUCKETS).toInt().coerceIn(0, HUE_BUCKETS - 1)
            bucketPts[bucket].add(Offset(hx + dx, hy + dy))
        }

        val sat   = (0.65f + snap.bands[2] * 0.35f).coerceIn(0f, 1f)
        val value = (0.45f + snap.bands[0] * 0.45f + bf * 0.1f).coerceIn(0f, 1f)

        // One drawPoints call per non-empty bucket — 16 calls max vs 2800
        for (b in 0 until HUE_BUCKETS) {
            val pts = bucketPts[b]
            if (pts.isEmpty()) continue
            val hue   = (b + 0.5f) / HUE_BUCKETS * 360f
            val color = hsvColor(hue, sat, value)
            drawPoints(pts, PointMode.Points, color, strokeWidth = dotPx, cap = StrokeCap.Round)
        }
    }
}

private fun hsvColor(h: Float, s: Float, v: Float): Color {
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    return when {
        h < 60f  -> Color(c + m, x + m, m)
        h < 120f -> Color(x + m, c + m, m)
        h < 180f -> Color(m, c + m, x + m)
        h < 240f -> Color(m, x + m, c + m)
        h < 300f -> Color(x + m, m, c + m)
        else     -> Color(c + m, m, x + m)
    }
}
