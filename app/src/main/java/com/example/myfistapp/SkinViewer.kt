package com.example.myfistapp

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class ViewerMode {
    object ReadOnly  : ViewerMode()
    object PreCommit : ViewerMode()
}

@Composable
fun SkinViewerDialog(
    skinFile: File,
    sourceLabel: String,
    mode: ViewerMode,
    onClose: () -> Unit,
    onKeep: (() -> Unit)? = null,
    onTryAgain: (() -> Unit)? = null,
) {
    var bitmap by remember(skinFile.absolutePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(skinFile.absolutePath) {
        withContext(Dispatchers.IO) {
            bitmap = BitmapFactory.decodeFile(skinFile.absolutePath)
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var containerWidthPx by remember { mutableIntStateOf(0) }

    // Reset zoom/pan when skin changes.
    LaunchedEffect(skinFile.absolutePath) {
        scale = 1f
        offsetX = 0f
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0F)),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Top bar ──────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A24))
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text     = skinFile.name,
                            color    = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text     = sourceLabel,
                            color    = DimWhite,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DimWhite)
                    }
                }

                // ── Strip body ───────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF0A0A0F)),
                    contentAlignment = Alignment.Center,
                ) {
                    val bm = bitmap
                    if (bm != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { containerWidthPx = it.width }
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val newScale = (scale * zoom).coerceIn(1f, 16f)
                                            scale = newScale
                                            val maxOffset = (newScale - 1f) * containerWidthPx / 2f
                                            offsetX = (offsetX + pan.x).coerceIn(-maxOffset, maxOffset)
                                        }
                                    },
                            ) {
                                Image(
                                    bitmap = bm.asImageBitmap(),
                                    contentDescription = sourceLabel,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer(
                                            scaleX = scale,
                                            scaleY = scale,
                                            translationX = offsetX,
                                        ),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text  = "${bm.width} × ${bm.height} · ${formatFileSize(skinFile.length())}",
                                color = DimWhite.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                            )
                        }
                    } else {
                        CircularProgressIndicator(color = NeonCyan)
                    }
                }

                // ── Bottom bar ───────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A24))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (mode) {
                        ViewerMode.ReadOnly -> {
                            Button(
                                onClick = onClose,
                                colors  = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            ) {
                                Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                        ViewerMode.PreCommit -> {
                            OutlinedButton(
                                onClick  = { onTryAgain?.invoke() },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Try Again")
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick  = onClose,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Cancel", color = DimWhite)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick  = { onKeep?.invoke() },
                                modifier = Modifier.weight(1f),
                                colors   = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            ) {
                                Text("Keep", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SkinPickerDialog(
    builtinSnapshots: List<File>,
    userSlots: List<FilledSlot>,
    onPick: (File, String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(Color(0xFF1A1A24), RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("View skins", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = DimWhite)
                }
            }

            LazyColumn {
                if (builtinSnapshots.isNotEmpty()) {
                    item {
                        Text(
                            text     = "Built-in",
                            color    = DimWhite.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    items(builtinSnapshots) { file ->
                        val name  = file.nameWithoutExtension
                            .split("_")
                            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
                        val label = "Built-in: $name"
                        SkinPickerRow(file = file, label = name) { onPick(file, label) }
                    }
                }
                if (userSlots.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text     = "Your skins",
                            color    = DimWhite.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    items(userSlots, key = { it.index }) { slot ->
                        val label = "Slot ${slot.index + 1}"
                        SkinPickerRow(file = slot.file, label = label) { onPick(slot.file, label) }
                    }
                }
                if (builtinSnapshots.isEmpty() && userSlots.isEmpty()) {
                    item {
                        Text(
                            "No skins available.",
                            color    = DimWhite,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick  = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Cancel", color = DimWhite)
            }
        }
    }
}

@Composable
private fun SkinPickerRow(file: File, label: String, onClick: () -> Unit) {
    var bitmap by remember(file.absolutePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file.absolutePath) {
        withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
            bitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2A2A38))
                .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val bm = bitmap
            if (bm != null) {
                Image(
                    bitmap             = bm.asImageBitmap(),
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(
                    color       = NeonCyan,
                    modifier    = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1_024L          -> "${bytes}B"
    bytes < 1_048_576L      -> "${bytes / 1_024}KB"
    else                    -> "${"%.1f".format(bytes.toFloat() / 1_048_576f)}MB"
}
