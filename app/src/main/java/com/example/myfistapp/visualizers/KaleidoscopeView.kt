package com.example.myfistapp.visualizers

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.example.myfistapp.audio.AudioFile
import kotlin.math.PI

private const val SEGMENTS = 12
private const val ROTATION_SPEED = 0.05f   // rad/s — ~125 s per full revolution

@Composable
fun KaleidoscopeCanvas(
    audioFile: AudioFile?,
    playbackFraction: Float,
    modifier: Modifier = Modifier,
) {
    val shader = remember { RuntimeShader(KALEIDOSCOPE_AGSL) }
    var rotation by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (lastNanos == 0L) 0.016f
                         else ((nanos - lastNanos) / 1_000_000_000f).coerceAtMost(0.1f)
                lastNanos = nanos
                rotation = (rotation + dt * ROTATION_SPEED) % (2f * PI.toFloat())
            }
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            // Reading `rotation` here subscribes this layer to recompose when
            // rotation changes, refreshing the shader uniform and RenderEffect.
            shader.setIntUniform("segments", SEGMENTS)
            shader.setFloatUniform("rotation", rotation)
            shader.setFloatUniform("resolution", size.width, size.height)
            renderEffect = RenderEffect
                .createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
        },
    ) {
        WarpfieldCanvas(
            audioFile = audioFile,
            playbackFraction = playbackFraction,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
