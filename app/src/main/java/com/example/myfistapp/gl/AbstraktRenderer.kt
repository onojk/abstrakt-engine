package com.example.myfistapp.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
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

private const val WARP_GRID_DIM      = 70f
private const val WARP_DOT_RADIUS    =  6f
private const val PAINTER_TEX_W      = 1024
private const val PAINTER_TEX_H      = 256
private const val PAINTER_STRIPE_W   = 16

internal class AbstraktRenderer : GLSurfaceView.Renderer {

    val audioUniforms = AudioUniforms()
    private val influencers = Influencers(audioUniforms)
    private val driftState  = DriftState()

    @Volatile var glMode: GlVizMode = GlVizMode.TEST

    fun setAudioFile(file: AudioFile?)  { audioUniforms.audioFile        = file }
    fun setPlaybackFraction(f: Float)   { audioUniforms.playbackFraction = f    }

    private var testProgram:    ShaderProgram? = null
    private var warpProgram:    ShaderProgram? = null
    private var driftProgram:   ShaderProgram? = null
    private var cycloneProgram: ShaderProgram? = null
    private val painterPrograms: MutableMap<Painter, ShaderProgram> = mutableMapOf()
    private var vaoId           = 0
    private var vboId           = 0
    private var cycloneVaoId    = 0
    private var cycloneVboId    = 0
    private var cycloneAngleRad = 0f
    private val cylinderVertexCount = CylinderGeometry.VERTEX_COUNT
    private var painterFBO      = 0
    private var painterTexture  = 0
    private var beatDecay       = 0f
    // FBO fields kept for createFBO/destroyFBO helpers — not used by Drift's polar branch.
    private var fboId        = 0
    private var fboTexId     = 0
    private var surfaceWidth  = 1
    private var surfaceHeight = 1
    private var startTimeNs   = 0L
    private var lastFrameNs   = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "onSurfaceCreated — building GL resources")
        testProgram     = null
        warpProgram     = null
        driftProgram    = null
        cycloneProgram  = null
        painterPrograms.clear()
        vaoId           = 0
        vboId           = 0
        cycloneVaoId    = 0
        cycloneVboId    = 0
        cycloneAngleRad = 0f
        painterFBO      = 0
        painterTexture  = 0
        fboId           = 0
        fboTexId        = 0
        startTimeNs     = 0L
        lastFrameNs     = 0L
        audioUniforms.uniformsLogged = false

        GLES30.glClearColor(0f, 0f, 0f, 1f)

        testProgram    = ShaderProgram(Shaders.TEST_VERT,    Shaders.TEST_FRAG)
        warpProgram    = ShaderProgram(Shaders.WARP_VERT,    Shaders.WARP_FRAG)
        driftProgram   = ShaderProgram(Shaders.TEST_VERT,    Shaders.DRIFT_POLAR_FRAG)
        cycloneProgram = ShaderProgram(Shaders.CYCLONE_VERT, Shaders.CYCLONE_FRAG)
        allPainters().forEach { entry ->
            painterPrograms[entry.painter] = ShaderProgram(entry.vert, entry.frag)
        }

        // ── Fullscreen quad VAO/VBO (shared by 2D modes) ─────────────────────
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

        // ── Cylinder VAO/VBO (Cyclone mode) ──────────────────────────────────
        val cycVaos = IntArray(1)
        GLES30.glGenVertexArrays(1, cycVaos, 0)
        cycloneVaoId = cycVaos[0]
        GLES30.glBindVertexArray(cycloneVaoId)

        val cycVbos = IntArray(1)
        GLES30.glGenBuffers(1, cycVbos, 0)
        cycloneVboId = cycVbos[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, cycloneVboId)

        val cylData = CylinderGeometry.vertices
        val cylBuf  = ByteBuffer
            .allocateDirect(cylData.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        cylBuf.put(cylData).position(0)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            cylData.size * Float.SIZE_BYTES,
            cylBuf,
            GLES30.GL_STATIC_DRAW,
        )

        val stride = CylinderGeometry.STRIDE_BYTES
        // a_position: location 0, vec3, offset 0
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        // a_uv: location 1, vec2, offset 12 bytes (3 floats)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, 12)

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        // ── Painter texture + FBO (Cyclone rolling paint) ────────────────────
        val pTexIds = IntArray(1)
        GLES30.glGenTextures(1, pTexIds, 0)
        painterTexture = pTexIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, painterTexture)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
            PAINTER_TEX_W, PAINTER_TEX_H, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null,
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        // GL_REPEAT in S lets the cylinder UV wrap cleanly at the seam.
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        val pFboIds = IntArray(1)
        GLES30.glGenFramebuffers(1, pFboIds, 0)
        painterFBO = pFboIds[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, painterFBO)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, painterTexture, 0,
        )
        val painterStatus = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        Log.d(TAG, "painterFBO ${PAINTER_TEX_W}x${PAINTER_TEX_H}: ${
            if (painterStatus == GLES30.GL_FRAMEBUFFER_COMPLETE) "COMPLETE"
            else "ERROR 0x${painterStatus.toString(16)}"
        }")
        // One-time clear to black — thereafter only scissored stripe writes touch it.
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        Log.d(TAG, "GL resources ready: quad vao=$vaoId cyclone vao=$cycloneVaoId " +
            "test=${testProgram?.id} warp=${warpProgram?.id} " +
            "drift=${driftProgram?.id} cyclone=${cycloneProgram?.id} " +
            "painters=${painterPrograms.mapValues { it.value.id }}")
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

            GlVizMode.CYCLONE -> {
                val snap  = audioUniforms.getSnapshot()
                beatDecay = if (snap.isBeat) 1.0f
                            else (beatDecay * Math.exp((-dt * 5.0).toDouble()).toFloat())
                                .coerceAtLeast(0f)

                val cProg = cycloneProgram ?: return
                val pProg = painterPrograms[audioUniforms.activePainter] ?: return

                // Advance rotation: 2π radians per 30 seconds.
                cycloneAngleRad += dt * (2f * Math.PI.toFloat() / 30f)

                // ── Pass 1: Painter — write a hue stripe at the rear angle ───
                // Rear is 270° past front (front = α+π/2, rear = α+3π/2).
                val rearU = ((cycloneAngleRad + 3.0 * Math.PI / 2.0)
                    .mod(2.0 * Math.PI) / (2.0 * Math.PI)).toFloat()
                val rearX = (rearU * PAINTER_TEX_W).toInt().coerceIn(0, PAINTER_TEX_W - 1)
                val endX  = rearX + PAINTER_STRIPE_W

                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, painterFBO)
                GLES30.glViewport(0, 0, PAINTER_TEX_W, PAINTER_TEX_H)
                GLES30.glEnable(GLES30.GL_SCISSOR_TEST)
                pProg.use()
                // Full painter contract — unused uniforms are optimized out (loc=-1, no-op).
                pProg.setFloat("u_time", timeSec)
                pProg.setFloat("u_peak", snap.peak)
                pProg.setFloat("u_beat", if (snap.isBeat) 1f else 0f)
                pProg.setFloat("u_beat_decay", beatDecay)
                pProg.setFloat("u_playback_fraction", audioUniforms.playbackFraction)
                pProg.setFloatArray("u_bands", snap.bands)
                if (endX <= PAINTER_TEX_W) {
                    GLES30.glScissor(rearX, 0, PAINTER_STRIPE_W, PAINTER_TEX_H)
                    drawQuad()
                } else {
                    // Stripe wraps past the right edge — two scissored draws.
                    GLES30.glScissor(rearX, 0, PAINTER_TEX_W - rearX, PAINTER_TEX_H)
                    drawQuad()
                    GLES30.glScissor(0, 0, endX - PAINTER_TEX_W, PAINTER_TEX_H)
                    drawQuad()
                }
                GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

                // ── Pass 2: Cylinder — sample painter texture, draw 3D mesh ──
                GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
                GLES30.glClearColor(0.12f, 0.12f, 0.12f, 1f)
                GLES30.glEnable(GLES30.GL_DEPTH_TEST)
                GLES30.glEnable(GLES30.GL_CULL_FACE)
                GLES30.glCullFace(GLES30.GL_BACK)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

                val modelM = FloatArray(16)
                val viewM  = FloatArray(16)
                val projM  = FloatArray(16)
                val vpM    = FloatArray(16)
                val mvpM   = FloatArray(16)

                Matrix.setIdentityM(modelM, 0)
                Matrix.rotateM(modelM, 0,
                    Math.toDegrees(cycloneAngleRad.toDouble()).toFloat(), 0f, 1f, 0f)
                Matrix.setLookAtM(viewM, 0,
                    0f, 0f, 3f, 0f, 0f, 0f, 0f, 1f, 0f)
                Matrix.perspectiveM(projM, 0, 45f, wW / wH, 0.1f, 100f)
                Matrix.multiplyMM(vpM,  0, projM, 0, viewM,  0)
                Matrix.multiplyMM(mvpM, 0, vpM,   0, modelM, 0)

                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, painterTexture)

                cProg.use()
                cProg.setMat4("u_mvp", mvpM)
                cProg.setInt("u_painterTexture", 0)

                GLES30.glBindVertexArray(cycloneVaoId)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, cylinderVertexCount)
                GLES30.glBindVertexArray(0)

                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

                // Restore state for 2D modes.
                GLES30.glDisable(GLES30.GL_DEPTH_TEST)
                GLES30.glDisable(GLES30.GL_CULL_FACE)
                GLES30.glClearColor(0f, 0f, 0f, 1f)
            }
        }
    }

    private fun drawQuad() {
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }
}
