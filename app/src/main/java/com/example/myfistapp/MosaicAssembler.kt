package com.example.myfistapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

// Assembles 64 RGBA tile buffers (each 128×128, from glReadPixels) into a 4096×256 JPEG skin.
// 32 columns × 2 rows = 4096×256 — same output shape as MosaicGenerator / SkinProcessor skins.
// Handles RGBA→ARGB conversion and Y-flip (GL origin is bottom-left, Bitmap is top-left).
object MosaicAssembler {

    private const val TILE = 128
    private const val COLS = 32
    private const val ROWS = 2

    suspend fun assembleCaptureMosaic(
        @Suppress("UNUSED_PARAMETER") context: Context,
        tileBuffers: List<ByteBuffer>,
        outputFile: File,
    ): File = withContext(Dispatchers.IO) {
        require(tileBuffers.size == COLS * ROWS) {
            "Expected ${COLS * ROWS} tile buffers, got ${tileBuffers.size}"
        }

        val canvas = Bitmap.createBitmap(COLS * TILE, ROWS * TILE, Bitmap.Config.ARGB_8888)
        val painter = Canvas(canvas)

        try {
            for (row in 0 until ROWS) {
                for (col in 0 until COLS) {
                    val tileIdx = row * COLS + col
                    val buf = tileBuffers[tileIdx]
                    buf.rewind()

                    // Read raw RGBA ints; on little-endian Android they arrive as ABGR packed.
                    val rawInts = IntArray(TILE * TILE)
                    buf.asIntBuffer().get(rawInts)

                    // ABGR→ARGB + Y-flip in one pass (GL rows are bottom-to-top).
                    val pixels = IntArray(TILE * TILE)
                    for (y in 0 until TILE) {
                        val srcRow = TILE - 1 - y
                        val srcBase = srcRow * TILE
                        val dstBase = y * TILE
                        for (x in 0 until TILE) {
                            val abgr = rawInts[srcBase + x]
                            pixels[dstBase + x] =
                                (abgr and 0xFF00FF00.toInt()) or
                                ((abgr and 0xFF) shl 16) or
                                ((abgr ushr 16) and 0xFF)
                        }
                    }

                    val tileBmp = Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ARGB_8888)
                    tileBmp.setPixels(pixels, 0, TILE, 0, 0, TILE, TILE)
                    val dstX = col * TILE
                    val dstY = row * TILE
                    painter.drawBitmap(
                        tileBmp,
                        Rect(0, 0, TILE, TILE),
                        Rect(dstX, dstY, dstX + TILE, dstY + TILE),
                        null,
                    )
                    tileBmp.recycle()
                }
            }
        } catch (e: Exception) {
            canvas.recycle()
            throw e
        }

        outputFile.parentFile?.mkdirs()
        try {
            FileOutputStream(outputFile).use { fos ->
                canvas.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
        } finally {
            canvas.recycle()
        }

        outputFile
    }
}
