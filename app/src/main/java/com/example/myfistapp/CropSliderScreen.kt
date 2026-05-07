package com.example.myfistapp

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val CropBg   = Color(0xFF0A0A0F)
private val CropCyan = Color(0xFF00FFFF)
private val CropDim  = Color(0x99FFFFFF)

@Composable
fun CropSliderScreen(
    photoUri: Uri,
    onCancel: () -> Unit,
    onConfirm: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenH = LocalConfiguration.current.screenHeightDp.dp

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sliderValue   by remember { mutableFloatStateOf(0.5f) }
    var isProcessing  by remember { mutableStateOf(false) }
    var isPanorama    by remember { mutableStateOf(false) }
    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Fix: state mutation must happen on the Main thread after IO work completes.
    LaunchedEffect(photoUri) {
        val bmp   = withContext(Dispatchers.IO) { loadPreviewBitmap(context, photoUri) }
        isPanorama    = bmp != null && bmp.width.toFloat() / bmp.height >= 16f
        previewBitmap = bmp
    }

    // Live strip preview: recompute on Dispatchers.Default on every slider drag.
    // collectLatest cancels stale in-flight computation when slider moves again.
    LaunchedEffect(previewBitmap, isPanorama) {
        val bmp = previewBitmap ?: run { croppedBitmap = null; return@LaunchedEffect }
        snapshotFlow { sliderValue }
            .collectLatest { sv ->
                val crop = computeCropRegion(bmp.width, bmp.height, sv, isPanorama)
                croppedBitmap = if (crop.w > 0 && crop.h > 0) {
                    withContext(Dispatchers.Default) {
                        Bitmap.createBitmap(bmp, crop.x, crop.y, crop.w, crop.h)
                    }
                } else null
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CropBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text         = "crop skin",
                color        = CropCyan,
                fontSize     = 18.sp,
                fontWeight   = FontWeight.Bold,
                letterSpacing = 2.sp,
            )

            Spacer(Modifier.height(14.dp))

            // Source photo at natural aspect ratio, capped at 50% screen height,
            // with a live cyan rect showing the exact crop region.
            val bmp = previewBitmap
            if (bmp == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = CropCyan, strokeWidth = 2.dp)
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val maxWPx   = constraints.maxWidth.toFloat()
                    val maxHPx   = with(density) { (screenH * 0.5f).toPx() }
                    val scale    = minOf(maxWPx / bmp.width, maxHPx / bmp.height)
                    val dispW    = with(density) { (bmp.width  * scale).toDp() }
                    val dispH    = with(density) { (bmp.height * scale).toDp() }

                    Canvas(
                        modifier = Modifier
                            .size(dispW, dispH)
                            .background(Color.Black),
                    ) {
                        // Draw source photo scaled to fill the Canvas.
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawBitmap(
                                bmp, null,
                                android.graphics.RectF(0f, 0f, size.width, size.height),
                                null,
                            )
                        }
                        // Reading sliderValue and isPanorama inside the draw block means Compose
                        // invalidates only this Canvas on drag — no parent recomposition needed.
                        val crop = computeCropRegion(bmp.width, bmp.height, sliderValue, isPanorama)
                        val sx   = size.width  / bmp.width
                        val sy   = size.height / bmp.height
                        drawRect(
                            color   = CropCyan,
                            topLeft = Offset(crop.x * sx, crop.y * sy),
                            size    = Size(crop.w * sx, crop.h * sy),
                            style   = Stroke(width = 4f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text     = "Preview",
                color    = CropDim,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Start),
            )

            Spacer(Modifier.height(4.dp))

            // 16:1 strip showing the exact cropped region, updated live as slider drags.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val cb = croppedBitmap
                if (cb != null) {
                    Image(
                        bitmap             = cb.asImageBitmap(),
                        contentDescription = null,
                        contentScale       = ContentScale.FillBounds,
                        modifier           = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text     = "drag to position",
                color    = CropDim,
                fontSize = 12.sp,
            )

            Slider(
                value         = sliderValue,
                onValueChange = { sliderValue = it },
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                colors = SliderDefaults.colors(
                    thumbColor       = CropCyan,
                    activeTrackColor = CropCyan,
                ),
            )

            Spacer(Modifier.weight(1f))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick  = onCancel,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                ) {
                    Text("Cancel", color = Color.White)
                }
                Button(
                    onClick = {
                        scope.launch {
                            isProcessing = true
                            try {
                                val file = processSkinFromUri(context, photoUri, sliderValue)
                                onConfirm(file)
                            } catch (e: Exception) {
                                Log.w("CropSlider", "processing failed: ${e.message}")
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled  = !isProcessing && previewBitmap != null,
                    colors   = ButtonDefaults.buttonColors(containerColor = CropCyan),
                ) {
                    Text("Use This", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = CropCyan)
            }
        }
    }
}
