package com.onojk.abstrakt.gl

import android.opengl.GLES30
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "CaptureMosaic"
internal const val CAPTURE_TILES = 64
private const val TILE_SIZE = 128

class CaptureMosaicController {

    private class CaptureRequest(
        val tileRegions: List<Pair<Int, Int>>,
        val tilesCaptured: AtomicInteger = AtomicInteger(0),
        val tileBuffers: Array<ByteBuffer> = Array(CAPTURE_TILES) {
            ByteBuffer.allocateDirect(TILE_SIZE * TILE_SIZE * 4).order(ByteOrder.nativeOrder())
        },
        val completed: AtomicBoolean = AtomicBoolean(false),
        val onComplete: (List<ByteBuffer>) -> Unit,
        val onCancel: () -> Unit,
    )

    @Volatile private var captureRequest: CaptureRequest? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Returns the number of tiles captured so far (0 if idle). */
    val capturedCount: Int get() = captureRequest?.tilesCaptured?.get() ?: 0

    fun requestCapture(
        fboWidth: Int,
        fboHeight: Int,
        onComplete: (List<ByteBuffer>) -> Unit,
        onCancel: () -> Unit,
    ) {
        if (fboWidth < TILE_SIZE || fboHeight < TILE_SIZE) {
            Log.w(TAG, "FBO too small for capture: ${fboWidth}x${fboHeight}")
            mainHandler.post { onCancel() }
            return
        }
        val rng  = Random()
        val maxX = fboWidth  - TILE_SIZE
        val maxY = fboHeight - TILE_SIZE
        val regions = List(CAPTURE_TILES) {
            Pair(
                if (maxX > 0) rng.nextInt(maxX + 1) else 0,
                if (maxY > 0) rng.nextInt(maxY + 1) else 0,
            )
        }
        captureRequest = CaptureRequest(
            tileRegions = regions,
            onComplete  = onComplete,
            onCancel    = onCancel,
        )
        Log.d(TAG, "Capture requested: $CAPTURE_TILES tiles from ${fboWidth}x${fboHeight} FBO")
    }

    /**
     * Called from the GL thread once per frame, after the kaleido pass writes to its FBO
     * and before the frame overlay pass.
     */
    fun captureTileIfActive(kaleidoFbo: Int, fboW: Int, fboH: Int) {
        val req     = captureRequest ?: return
        val tileIdx = req.tilesCaptured.get()
        if (tileIdx >= CAPTURE_TILES) return

        val (srcX, srcY) = req.tileRegions[tileIdx]
        val buf = req.tileBuffers[tileIdx]
        buf.clear()

        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, kaleidoFbo)
        GLES30.glReadPixels(srcX, srcY, TILE_SIZE, TILE_SIZE,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 0)
        buf.rewind()

        val n = req.tilesCaptured.incrementAndGet()
        if (n == CAPTURE_TILES) {
            captureRequest = null
            if (req.completed.compareAndSet(false, true)) {
                val buffers = req.tileBuffers.toList()
                mainHandler.post { req.onComplete(buffers) }
                Log.d(TAG, "Capture complete: $CAPTURE_TILES tiles")
            }
        }
    }

    fun cancel() {
        val req = captureRequest ?: return
        captureRequest = null
        if (req.completed.compareAndSet(false, true)) {
            mainHandler.post { req.onCancel() }
            Log.d(TAG, "Capture cancelled")
        }
    }
}
