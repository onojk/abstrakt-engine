package com.example.myfistapp.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import com.example.myfistapp.audio.AudioFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val TAG = "AbstraktGL"

private val QUAD_VERTS = floatArrayOf(
    -1f, -1f,
     1f, -1f,
    -1f,  1f,
     1f,  1f,
)

private const val WARP_GRID_DIM   = 70f
private const val WARP_DOT_RADIUS =  6f

internal class AbstraktRenderer : GLSurfaceView.Renderer {

    val audioUniforms = AudioUniforms()
    private val influencers = Influencers(audioUniforms)
    private val driftState  = DriftState()

    @Volatile var glMode: GlVizMode = GlVizMode.TEST

    fun setAudioFile(file: AudioFile?)  { audioUniforms.audioFile        = file }
    fun setPlaybackFraction(f: Float)   { audioUniforms.playbackFraction = f    }

    private var testProgram:  ShaderProgram? = null
    private var warpProgram:  ShaderProgram? = null
    private var driftProgram: ShaderProgram? = null
    private var vaoId        = 0
    private var vboId        = 0
    private var beatDecay    = 0f
    // FBO fields kept for createFBO/destroyFBO helpers — not used by Drift's polar branch.
    private var fboId        = 0
    private var fboTexId     = 0
    private var surfaceWidth  = 1
    private var surfaceHeight = 1
    private var startTimeNs   = 0L
    private var lastFrameNs   = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "onSurfaceCreated — building GL resources")
        testProgram  = null
        warpProgram  = null
        driftProgram = null
        vaoId        = 0
        vboId        = 0
        fboId        = 0
        fboTexId     = 0
        startTimeNs  = 0L
        lastFrameNs  = 0L
        audioUniforms.uniformsLogged = false

        GLES30.glClearColor(0f, 0f, 0f, 1f)

        testProgram  = ShaderProgram(Shaders.TEST_VERT, Shaders.TEST_FRAG)
        warpProgram  = ShaderProgram(Shaders.WARP_VERT, Shaders.WARP_FRAG)
        driftProgram = ShaderProgram(Shaders.TEST_VERT, Shaders.DRIFT_POLAR_FRAG)

        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        vaoId = vaos[0]
        GLES30.glBindVertexArray(vaoId)

        val vbos = IntArray(1)
        GLES30.glGenBuffers(1, vbos, 0)
        vboId = vbos[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)

        val buf = ByteBuffer
            .allocateDirect(QUAD_VERTS.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buf.put(QUAD_VERTS)
        buf.position(0)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            QUAD_VERTS.size * Float.SIZE_BYTES,
            buf,
            GLES30.GL_STATIC_DRAW,
        )

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        Log.d(TAG, "GL resources ready: vao=$vaoId " +
            "test=${testProgram?.id} warp=${warpProgram?.id} drift=${driftProgram?.id}")
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        surfaceWidth  = w
        surfaceHeight = h
        GLES30.glViewport(0, 0, w, h)
        Log.d(TAG, "onSurfaceChanged: ${w}x${h}")
    }

    // ── FBO helpers — available for future two-pass visualizers. ──────────────
    // Call createFBO from onSurfaceChanged and destroyFBO from onSurfaceCreated
    // (context loss) when a mode that needs them is active.

    private fun destroyFBO() {
        if (fboTexId != 0) { GLES30.glDeleteTextures(1, intArrayOf(fboTexId), 0); fboTexId = 0 }
        if (fboId    != 0) { GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0); fboId = 0 }
    }

    @Suppress("unused")
    private fun createFBO(w: Int, h: Int) {
        destroyFBO()

        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        fboTexId = texIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexId)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null,
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        val fboIds = IntArray(1)
        GLES30.glGenFramebuffers(1, fboIds, 0)
        fboId = fboIds[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, fboTexId, 0,
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        Log.d(TAG, "FBO ${w}x${h}: ${
            if (status == GLES30.GL_FRAMEBUFFER_COMPLETE) "COMPLETE"
            else "ERROR 0x${status.toString(16)}"
        }")
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNs = System.nanoTime()
        if (startTimeNs == 0L) { startTimeNs = nowNs; lastFrameNs = nowNs }
        val timeSec = (nowNs - startTimeNs) / 1_000_000_000f
        val dt      = ((nowNs - lastFrameNs) / 1_000_000_000f).coerceAtMost(0.1f)
        lastFrameNs = nowNs

        val wW = surfaceWidth.toFloat()
        val wH = surfaceHeight.toFloat()

        when (glMode) {
            GlVizMode.TEST -> {
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                val prog = testProgram ?: return
                prog.use()
                prog.setVec2("u_resolution", wW, wH)
                audioUniforms.applyToProgram(prog, timeSec)
                drawQuad()
            }

            GlVizMode.WARP -> {
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                val prog = warpProgram ?: return
                prog.use()
                prog.setVec2("u_resolution", wW, wH)
                prog.setFloat("u_grid_dim",   WARP_GRID_DIM)
                prog.setFloat("u_dot_radius", WARP_DOT_RADIUS)
                audioUniforms.applyToProgram(prog, timeSec)
                influencers.updateForFrame(timeSec, dt, wW, wH)
                influencers.applyToProgram(prog)
                drawQuad()
            }

            GlVizMode.DRIFT -> {
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                val prog = driftProgram ?: return
                val snap = audioUniforms.getSnapshot()
                // Spike beatDecay to 1.0 on beat, then decay with τ ≈ 200ms.
                beatDecay = if (snap.isBeat) 1.0f
                            else (beatDecay * Math.exp((-dt * 5.0).toDouble()).toFloat())
                                .coerceAtLeast(0f)
                driftState.update(timeSec, snap.bands)
                prog.use()
                audioUniforms.applyToProgram(prog, timeSec)
                driftState.applyToProgram(prog)
                prog.setFloat("u_beat_decay", beatDecay)
                drawQuad()
            }
        }
    }

    private fun drawQuad() {
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }
}
