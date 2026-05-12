package com.onojk.abstrakt.visualizers

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
import com.onojk.abstrakt.audio.AudioFile
import kotlin.math.PI

private const val SEGMENTS = 12
private const val ROTATION_SPEED = 0.05f   // rad/s — ~125 s per full revolution

private const val STRINGS_ENABLED = true

private typealias EchoLayer = @Composable (AudioFile?, Float, Modifier) -> Unit

private val warpfieldLayer: EchoLayer = { audioFile, playbackFraction, modifier ->
    WarpfieldCanvas(audioFile = audioFile, playbackFraction = playbackFraction, modifier = modifier)
}

private val stringsLayer: EchoLayer = { audioFile, playbackFraction, modifier ->
    GuitarStringsLayer(audioFile = audioFile, playbackFraction = playbackFraction, modifier = modifier)
}

@Composable
fun KaleidoscopeCanvas(
    audioFile: AudioFile?,
    playbackFraction: Float,
    modifier: Modifier = Modifier,
) {
    val shader = remember { RuntimeShader(KALEIDOSCOPE_AGSL) }
    var rotation by remember { mutableFloatStateOf(0f) }

    val layers = remember {
        if (STRINGS_ENABLED) listOf(warpfieldLayer, stringsLayer)
        else listOf(warpfieldLayer)
    }

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
            shader.setIntUniform("segments", SEGMENTS)
            shader.setFloatUniform("rotation", rotation)
            shader.setFloatUniform("resolution", size.width, size.height)
            renderEffect = RenderEffect
                .createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
        },
    ) {
        layers.forEach { layer ->
            layer(audioFile, playbackFraction, Modifier.fillMaxSize())
        }
    }
}
