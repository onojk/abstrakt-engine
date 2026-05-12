package com.onojk.abstrakt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Random

// Generates a 4096×256 patchwork skin from a 16×2 grid of random 128×128 tiles, then applies
// the mirror-seam pass to produce a seamlessly-repeating 4096×256 output.
object MosaicGenerator {

    private const val TILE = 128
    private const val COLS = 16    // columns before mirror; final = 32
    private const val ROWS = 2
    private const val PRE_W = COLS * TILE   // 2048
    private const val H     = ROWS * TILE   // 256

    suspend fun generateMosaic(
        context: Context,
        sources: List<File>,
        outputFile: File,
    ): File = withContext(Dispatchers.IO) {
        require(sources.isNotEmpty()) { "sources list is empty" }

        val rng    = Random()
        val canvas = Bitmap.createBitmap(PRE_W, H, Bitmap.Config.ARGB_8888)
        val paint  = Canvas(canvas)
        val cache  = mutableMapOf<File, Bitmap?>()

        try {
            for (row in 0 until ROWS) {
                for (col in 0 until COLS) {
                    val src    = sources[rng.nextInt(sources.size)]
                    val srcBmp = cache.getOrPut(src) { BitmapFactory.decodeFile(src.absolutePath) }
                               ?: continue  // skip tile if decode fails

                    val maxSrcX = (srcBmp.width  - TILE).coerceAtLeast(0)
                    val maxSrcY = (srcBmp.height - TILE).coerceAtLeast(0)
                    val srcX    = if (maxSrcX > 0) rng.nextInt(maxSrcX + 1) else 0
                    // Two valid row origins in a 256-tall bitmap: 0 or 128.
                    val srcY    = (rng.nextInt(ROWS) * TILE).coerceAtMost(maxSrcY)

                    val dstX = col * TILE
                    val dstY = row * TILE
                    paint.drawBitmap(
                        srcBmp,
                        Rect(srcX, srcY, srcX + TILE, srcY + TILE),
                        Rect(dstX, dstY, dstX + TILE, dstY + TILE),
                        null,
                    )
                }
            }
        } finally {
            cache.values.forEach { it?.recycle() }
        }

        // Mirror seam: [2048-wide original | 2048-wide flipped] → 4096×256 seamless output.
        // applyMirrorSeam() recycles the canvas bitmap.
        val mirrored = applyMirrorSeam(canvas)
        try {
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                mirrored.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
        } finally {
            mirrored.recycle()
        }

        outputFile
    }
}
