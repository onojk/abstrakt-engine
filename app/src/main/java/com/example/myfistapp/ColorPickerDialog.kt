package com.example.myfistapp

import android.graphics.Color.HSVToColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController

@Composable
fun FrameColorPickerDialog(
    initialColorArgb: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val channels   = remember(initialColorArgb) { argbToIndependentChannels(initialColorArgb) }
    val controller = rememberColorPickerController()
    var pureColor  by remember { mutableStateOf(hsToPureColor(channels[0], channels[1])) }
    var lightness  by remember { mutableFloatStateOf(channels[2]) }
    var alpha      by remember { mutableFloatStateOf(channels[3]) }

    val mixed      = mixWithLightness(pureColor, lightness)
    val finalColor = mixed.copy(alpha = alpha)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF1A1A24),
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Frame color", color = NeonCyan)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                ) {
                    Checkerboard(Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(finalColor))
                }
            }
        },
        text = {
            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Color", color = DimWhite, style = MaterialTheme.typography.labelMedium)
                HsvColorPicker(
                    modifier       = Modifier.fillMaxWidth().height(200.dp).padding(8.dp),
                    controller     = controller,
                    initialColor   = pureColor,
                    onColorChanged = { envelope ->
                        val c   = envelope.color
                        val max = maxOf(c.red, c.green, c.blue).coerceAtLeast(0.001f)
                        pureColor = Color(red = c.red / max, green = c.green / max, blue = c.blue / max)
                    },
                )

                Text("Lightness", color = DimWhite, style = MaterialTheme.typography.labelMedium)
                GradientSlider(
                    value            = lightness,
                    onChange         = { lightness = it },
                    brush            = Brush.linearGradient(listOf(Color.Black, pureColor, Color.White)),
                    showCheckerboard = false,
                )

                Text("Opacity", color = DimWhite, style = MaterialTheme.typography.labelMedium)
                GradientSlider(
                    value            = alpha,
                    onChange         = { alpha = it },
                    brush            = Brush.linearGradient(listOf(Color.Transparent, mixed.copy(alpha = 1f))),
                    showCheckerboard = true,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text("Quick:", color = DimWhite)
                    QuickColorChip(Color.White,       "White") { onConfirm(0xFFFFFFFFL) }
                    QuickColorChip(Color.Black,       "Black") { onConfirm(0xFF000000L) }
                    QuickColorChip(Color(0xFF00FFFF), "Cyan")  { onConfirm(0xFF00FFFFL) }
                    QuickColorChip(Color.Transparent, "Clear") { onConfirm(0x00FFFFFFL) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(colorToArgbLong(finalColor)) }) {
                Text("Apply", color = NeonCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DimWhite)
            }
        },
    )
}

@Composable
private fun GradientSlider(
    value: Float,
    onChange: (Float) -> Unit,
    brush: Brush,
    showCheckerboard: Boolean,
) {
    Box(
        modifier         = Modifier.fillMaxWidth().height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            if (showCheckerboard) Checkerboard(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(brush))
        }
        Slider(
            value         = value,
            onValueChange = onChange,
            modifier      = Modifier.fillMaxWidth(),
            colors        = SliderDefaults.colors(
                thumbColor         = Color.White,
                activeTrackColor   = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
            ),
        )
    }
}

@Composable
internal fun Checkerboard(modifier: Modifier = Modifier) {
    val dark  = Color(0xFF888888)
    val light = Color(0xFFCCCCCC)
    Canvas(modifier = modifier) {
        val cell = 6.dp.toPx()
        val cols = (size.width  / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        for (row in 0..rows) {
            for (col in 0..cols) {
                drawRect(
                    color   = if ((row + col) % 2 == 0) light else dark,
                    topLeft = Offset(col * cell, row * cell),
                    size    = Size(cell, cell),
                )
            }
        }
    }
}

@Composable
private fun QuickColorChip(color: Color, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
    ) {
        if (color.alpha < 1f) Checkerboard(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(color))
    }
}

// Decomposes an ARGB Long into [hue 0-1, saturation 0-1, lightness 0-1, alpha 0-1]
// where lightness=0 → black, lightness=0.5 → pure hue/sat color, lightness=1 → white.
private fun argbToIndependentChannels(argb: Long): FloatArray {
    val a = ((argb shr 24) and 0xFFL).toFloat() / 255f
    val r = ((argb shr 16) and 0xFFL).toFloat() / 255f
    val g = ((argb shr  8) and 0xFFL).toFloat() / 255f
    val b = (argb           and 0xFFL).toFloat() / 255f

    val max   = maxOf(r, g, b)
    val min   = minOf(r, g, b)
    val delta = max - min

    // max < 1  → dark side only: lightness = max/2 (so range [0, 0.5))
    // max >= 1 → can have white mix: lightness = (min+1)/2 (range [0.5, 1])
    val lightness = if (max >= 1f) (min + 1f) / 2f else max / 2f

    val hue = if (delta < 0.001f) 0f else when {
        max == r -> (((g - b) / delta) % 6f + 6f) % 6f / 6f
        max == g -> ((b - r) / delta + 2f) / 6f
        else     -> ((r - g) / delta + 4f) / 6f
    }

    // pureColor (at V=1) always has min=0, so sat=1 for chromatic light colors.
    // Dark colors: pureColor = color/max, same HSV sat = delta/max.
    val saturation = when {
        max < 0.001f -> 0f
        max >= 1f    -> if (delta < 0.001f) 0f else 1f
        else         -> delta / max
    }

    return floatArrayOf(hue, saturation, lightness, a)
}

private fun hsToPureColor(hue: Float, saturation: Float): Color {
    val hsv = floatArrayOf(hue * 360f, saturation, 1f)
    return Color(HSVToColor(hsv))
}

private fun mixWithLightness(pureColor: Color, lightness: Float): Color =
    if (lightness < 0.5f) {
        val t = lightness * 2f
        Color(red = pureColor.red * t, green = pureColor.green * t, blue = pureColor.blue * t)
    } else {
        val t = (lightness - 0.5f) * 2f
        Color(
            red   = pureColor.red   + (1f - pureColor.red)   * t,
            green = pureColor.green + (1f - pureColor.green) * t,
            blue  = pureColor.blue  + (1f - pureColor.blue)  * t,
        )
    }

private fun colorToArgbLong(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL
