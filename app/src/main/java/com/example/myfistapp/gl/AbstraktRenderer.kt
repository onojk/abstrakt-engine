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

    // Audio state — written from main thread, read on GL thread via @Volatile fields inside.
    val audioUniforms = AudioUniforms()
    private val influencers = Influencers(audioUniforms)

    // Written from main thread, read on GL thread.
    @Volatile var glMode: GlVizMode = GlVizMode.TEST

    fun setAudioFile(file: AudioFile?)      { audioUniforms.audioFile        = file }
    fun setPlaybackFraction(f: Float)       { audioUniforms.playbackFraction = f    }

    private var testProgram: ShaderProgram? = null
    private var warpProgram: ShaderProgram? = null
    private var vaoId         = 0
    private var vboId         = 0
    private var surfaceWidth  = 1
    private var surfaceHeight = 1
    private var startTimeNs   = 0L
    private var lastFrameNs   = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "onSurfaceCreated — building GL resources")
        testProgram = null
        warpProgram = null
        vaoId       = 0
        vboId       = 0
        startTimeNs = 0L
        lastFrameNs = 0L
        audioUniforms.uniformsLogged = false

        GLES30.glClearColor(0f, 0f, 0f, 1f)

        testProgram = ShaderProgram(Shaders.TEST_VERT, Shaders.TEST_FRAG)
        warpProgram = ShaderProgram(Shaders.WARP_VERT, Shaders.WARP_FRAG)

        // Single VAO/VBO — both programs share layout(location=0) in vec2 a_position.
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

        Log.d(TAG, "GL resources ready: vao=$vaoId testProg=${testProgram?.id} warpProg=${warpProgram?.id}")
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        surfaceWidth  = w
        surfaceHeight = h
        GLES30.glViewport(0, 0, w, h)
        Log.d(TAG, "onSurfaceChanged: ${w}x${h}")
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNs = System.nanoTime()
        if (startTimeNs == 0L) { startTimeNs = nowNs; lastFrameNs = nowNs }
        val timeSec = (nowNs - startTimeNs) / 1_000_000_000f
        val dt      = ((nowNs - lastFrameNs) / 1_000_000_000f).coerceAtMost(0.1f)
        lastFrameNs = nowNs

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val wW = surfaceWidth.toFloat()
        val wH = surfaceHeight.toFloat()

        when (glMode) {
            GlVizMode.TEST -> {
                val prog = testProgram ?: return
                prog.use()
                prog.setVec2("u_resolution", wW, wH)
                audioUniforms.applyToProgram(prog, timeSec)
            }
            GlVizMode.WARP -> {
                val prog = warpProgram ?: return
                prog.use()
                prog.setVec2("u_resolution", wW, wH)
                prog.setFloat("u_grid_dim",   WARP_GRID_DIM)
                prog.setFloat("u_dot_radius", WARP_DOT_RADIUS)
                audioUniforms.applyToProgram(prog, timeSec)
                influencers.updateForFrame(timeSec, dt, wW, wH)
                influencers.applyToProgram(prog)
            }
        }

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }
}
