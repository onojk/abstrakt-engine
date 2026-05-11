package com.example.myfistapp.gl

import android.content.Context
import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.GLES30
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "PainterSnapshotter"

// Renders the 5 procedural built-in painters to 4096×256 JPEG snapshots.
// Snapshots are cached in filesDir/builtin_snapshots/; re-rendered only if missing.
// All GL work runs on the calling thread via a private EGL pbuffer context.
internal object PainterSnapshotter {

    private const val W = 4096
    private const val H = 256

    private val SNAPSHOT_PAINTERS = listOf(
        Painter.HUE_STRIPE,
        Painter.AUDIO_PAINT,
        Painter.PRINT_HEAD,
        Painter.SPIRAL,
        Painter.PLASMA,
    )

    // Deterministic synthetic audio for audio-reactive painters.
    private val SYNTH_BANDS = floatArrayOf(0.7f, 0.5f, 0.6f, 0.4f, 0.3f, 0.5f, 0.4f, 0.2f)
    private const val SYNTH_PEAK      = 0.6f
    private const val SYNTH_TIME      = 5.0f  // fixed u_time → deterministic, non-empty output

    suspend fun ensureBuiltinSnapshots(context: Context): List<File> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "builtin_snapshots").also { it.mkdirs() }
        val files = SNAPSHOT_PAINTERS.map { p -> File(dir, "${p.name.lowercase()}.jpg") }
        val missing = files.filter { !it.exists() || it.length() == 0L }
        if (missing.isNotEmpty()) {
            val painters = SNAPSHOT_PAINTERS.zip(files).filter { (_, f) -> !f.exists() || f.length() == 0L }
            try {
                renderToJpegs(painters.map { it.first }, painters.map { it.second })
            } catch (e: Exception) {
                Log.e(TAG, "Snapshot render failed: ${e.message}", e)
            }
        }
        files.filter { it.exists() && it.length() > 0L }
    }

    private fun renderToJpegs(painters: List<Painter>, outFiles: List<File>) {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }
        val ver = IntArray(2)
        EGL14.eglInitialize(display, ver, 0, ver, 1)

        // EGL2_BIT config works for ES3 contexts on all modern Android drivers.
        val cfgAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE,         8,
            EGL14.EGL_GREEN_SIZE,       8,
            EGL14.EGL_BLUE_SIZE,        8,
            EGL14.EGL_ALPHA_SIZE,       8,
            EGL14.EGL_RENDERABLE_TYPE,  EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE,     EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val cfgArr  = arrayOfNulls<android.opengl.EGLConfig>(1)
        val numCfg  = IntArray(1)
        EGL14.eglChooseConfig(display, cfgAttribs, 0, cfgArr, 0, 1, numCfg, 0)
        val cfg = cfgArr[0] ?: error("No EGL config found")

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        val ctx = EGL14.eglCreateContext(display, cfg, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        check(ctx != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }

        // 1×1 pbuffer — used only to make the context current; rendering goes to the FBO.
        val pbAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        val surface = EGL14.eglCreatePbufferSurface(display, cfg, pbAttribs, 0)
        check(surface != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" }

        EGL14.eglMakeCurrent(display, surface, surface, ctx)
        try {
            renderWithContext(painters, outFiles)
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, ctx)
            EGL14.eglTerminate(display)
        }
    }

    private fun renderWithContext(painters: List<Painter>, outFiles: List<File>) {
        // Fullscreen quad (same layout as PAINTER_VERT: a_position at location 0)
        val quadVerts = floatArrayOf(-1f, -1f,  1f, -1f,  -1f, 1f,  1f, 1f)
        val vaos = IntArray(1); GLES30.glGenVertexArrays(1, vaos, 0)
        val vbos = IntArray(1); GLES30.glGenBuffers(1, vbos, 0)
        GLES30.glBindVertexArray(vaos[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[0])
        val vb = ByteBuffer.allocateDirect(quadVerts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(quadVerts).position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quadVerts.size * 4, vb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)

        // FBO + RGBA texture at W×H
        val fbos = IntArray(1); GLES30.glGenFramebuffers(1, fbos, 0)
        val texs = IntArray(1); GLES30.glGenTextures(1, texs, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texs[0])
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
            W, H, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null,
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbos[0])
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, texs[0], 0,
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        check(status == GLES30.GL_FRAMEBUFFER_COMPLETE) { "FBO incomplete: 0x${status.toString(16)}" }

        GLES30.glViewport(0, 0, W, H)
        GLES30.glDisable(GLES30.GL_BLEND)

        val pixelBuf = ByteBuffer.allocateDirect(W * H * 4).order(ByteOrder.nativeOrder())

        try {
            for ((idx, painter) in painters.withIndex()) {
                val entry = allPainters().first { it.painter == painter }
                val prog  = ShaderProgram(entry.vert, entry.frag)
                try {
                    GLES30.glClearColor(0f, 0f, 0f, 1f)
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                    prog.use()
                    prog.setFloat("u_time",       SYNTH_TIME)
                    prog.setFloat("u_peak",       SYNTH_PEAK)
                    prog.setFloat("u_beat_decay", 0f)
                    prog.setFloatArray("u_bands", SYNTH_BANDS)
                    GLES30.glBindVertexArray(vaos[0])
                    GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
                    GLES30.glBindVertexArray(0)
                    GLES30.glFinish()

                    pixelBuf.clear()
                    GLES30.glReadPixels(0, 0, W, H, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixelBuf)
                    pixelBuf.rewind()

                    savePixelsAsJpeg(pixelBuf, outFiles[idx])
                    Log.d(TAG, "Saved ${painter.name} → ${outFiles[idx].name}")
                } finally {
                    prog.delete()
                }
            }
        } finally {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glDeleteFramebuffers(1, fbos, 0)
            GLES30.glDeleteTextures(1, texs, 0)
            GLES30.glDeleteBuffers(1, vbos, 0)
            GLES30.glDeleteVertexArrays(1, vaos, 0)
        }
    }

    // Converts the GL RGBA readback to an Android ARGB_8888 Bitmap (with Y-flip) and saves
    // as JPEG quality 90. On little-endian Android, glReadPixels RGBA bytes read as a
    // native-order int produce ABGR, so R and B must be swapped for Android ARGB format.
    private fun savePixelsAsJpeg(rgba: ByteBuffer, outFile: File) {
        val intSrc = IntArray(W * H)
        rgba.asIntBuffer().get(intSrc)

        // Swap R↔B and flip Y in one pass.
        val pixels = IntArray(W * H)
        for (y in 0 until H) {
            val srcRow = H - 1 - y   // GL is bottom-up
            val dstBase = y * W
            val srcBase = srcRow * W
            for (x in 0 until W) {
                val abgr = intSrc[srcBase + x]
                // ABGR → ARGB: keep A and G, swap R (bits 7:0) and B (bits 23:16)
                pixels[dstBase + x] = (abgr and 0xFF00FF00.toInt()) or
                                      ((abgr and 0xFF) shl 16)       or
                                      ((abgr ushr 16) and 0xFF)
            }
        }

        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, W, 0, 0, W, H)
        try {
            FileOutputStream(outFile).use { fos -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos) }
        } finally {
            bmp.recycle()
        }
    }
}
