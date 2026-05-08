package com.example.myfistapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.AlphaSlider
import com.github.skydoves.colorpicker.compose.AlphaTile
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController

@Composable
fun FrameColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val controller = rememberColorPickerController()
    var pickedColor by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF1A1A24),
        title = { Text("Frame color", color = NeonCyan) },
        text = {
            Column(
                modifier              = Modifier.fillMaxWidth(),
                horizontalAlignment   = Alignment.CenterHorizontally,
            ) {
                HsvColorPicker(
                    modifier     = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .padding(8.dp),
                    controller   = controller,
                    initialColor = initialColor,
                    onColorChanged = { envelope -> pickedColor = envelope.color },
                )
                Spacer(Modifier.height(8.dp))
                BrightnessSlider(
                    modifier   = Modifier.fillMaxWidth().height(36.dp),
                    controller = controller,
                )
                Spacer(Modifier.height(8.dp))
                AlphaSlider(
                    modifier   = Modifier.fillMaxWidth().height(36.dp),
                    controller = controller,
                )
                Spacer(Modifier.height(8.dp))
                AlphaTile(
                    modifier   = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                    controller = controller,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text("Quick:", color = DimWhite)
                    QuickColorChip(Color.White,              "White") { onConfirm(Color.White) }
                    QuickColorChip(Color.Black,              "Black") { onConfirm(Color.Black) }
                    QuickColorChip(Color(0xFF00FFFF),        "Cyan")  { onConfirm(Color(0xFF00FFFF)) }
                    QuickColorChip(Color.Transparent,        "Clear") { onConfirm(Color.Transparent) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickedColor) }) {
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
private fun QuickColorChip(color: Color, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
    )
}
