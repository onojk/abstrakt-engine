package com.onojk.abstrakt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "SkinProcessor"

internal data class CropRegion(val x: Int, val y: Int, val w: Int, val h: Int)

internal fun computeCropRegion(
    bmpW: Int,
    bmpH: Int,
    sliderPos: Float,
    isPanorama: Boolean,
): CropRegion = if (isPanorama) {
    val sliceW = (bmpH * 16).coerceAtMost(bmpW)
    val maxX   = (bmpW - sliceW).coerceAtLeast(0)
    val sliceX = if (maxX > 0) (sliderPos * maxX).toInt().coerceIn(0, maxX) else 0
    CropRegion(x = sliceX, y = 0, w = sliceW, h = bmpH)
} else {
    val sliceH = (bmpW / 16).coerceAtLeast(1)
    val maxY   = (bmpH - sliceH).coerceAtLeast(0)
    val sliceY = if (maxY > 0) (sliderPos * maxY).toInt().coerceIn(0, maxY) else 0
    CropRegion(x = 0, y = sliceY, w = bmpW, h = sliceH)
}

// Preview-resolution decode (max 2048 long-edge) — used by CropSliderScreen.
internal fun loadPreviewBitmap(context: Context, uri: Uri): Bitmap? =
    decodeSampledBitmap(context, uri, 2048)

suspend fun processSkinFromUri(
    context: Context,
    uri: Uri,
    sliderPosition: Float,
): File = withContext(Dispatchers.IO) {

    // a. Decode at up to 8192 long-edge to avoid OOM on huge photos.
    val source = decodeSampledBitmap(context, uri, 8192)
        ?: error("Failed to decode image from URI")

    val srcW      = source.width
    val srcH      = source.height
    val isPanorama = srcW.toFloat() / srcH >= 16f

    // b. 16:1 crop rect from slider position.
    val crop    = computeCropRegion(srcW, srcH, sliderPosition, isPanorama)
    val cropped = Bitmap.createBitmap(source, crop.x, crop.y, crop.w, crop.h)
    if (source !== cropped) source.recycle()

    // c. Resize so height = exactly 256.
    val scaledW = (cropped.width.toFloat() * 256f / cropped.height).toInt().coerceAtLeast(1)
    val resized  = Bitmap.createScaledBitmap(cropped, scaledW, 256, true)
    if (cropped !== resized) cropped.recycle()

    // d. Snap width DOWN to nearest 1024 multiple, min 1024, max 4096.
    val tier   = (resized.width / 1024).coerceIn(1, 4) * 1024
    val scaled = if (resized.width == tier) resized
                 else Bitmap.createScaledBitmap(resized, tier, 256, true)
    if (scaled !== resized) resized.recycle()

    // e. Mirror: [original | flipped] → seamless GL_REPEAT wrap.
    val finalBitmap = applyMirrorSeam(scaled)  // recycles scaled

    // f. Save to filesDir/user_skins/skin_<timestamp>.jpg
    val dir     = File(context.filesDir, "user_skins").also { it.mkdirs() }
    val outFile = File(dir, "skin_${System.currentTimeMillis()}.jpg")
    FileOutputStream(outFile).use { fos ->
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
    }
    finalBitmap.recycle()

    Log.d(TAG, "saved ${outFile.name} (${tier * 2}x256, tier=$tier)")
    outFile
}

// Doubles bitmap width by mirroring: [original | flipped] — ensures seamless GL_REPEAT wrapping.
// Recycles `input`.
internal fun applyMirrorSeam(input: Bitmap): Bitmap {
    val out = Bitmap.createBitmap(input.width * 2, input.height, Bitmap.Config.ARGB_8888)
    val c   = Canvas(out)
    c.drawBitmap(input, 0f, 0f, null)
    val m = Matrix().apply {
        preScale(-1f, 1f)
        postTranslate(input.width * 2f, 0f)
    }
    c.drawBitmap(input, m, null)
    input.recycle()
    return out
}

private fun decodeSampledBitmap(context: Context, uri: Uri, maxLongEdge: Int): Bitmap? {
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, boundsOpts)
    }
    val longEdge     = maxOf(boundsOpts.outWidth, boundsOpts.outHeight).coerceAtLeast(1)
    val inSampleSize = if (longEdge > maxLongEdge) Integer.highestOneBit(longEdge / maxLongEdge) else 1

    val decodeOpts = BitmapFactory.Options().apply {
        this.inSampleSize = inSampleSize
        inScaled = false
    }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, decodeOpts)
    }
}
