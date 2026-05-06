package com.example.myfistapp.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val TAG = "AbstraktGL"

// Fullscreen quad in NDC: two triangles via TRIANGLE_STRIP covering [-1,1]x[-1,1].
private val QUAD_VERTS = floatArrayOf(
    -1f, -1f,
     1f, -1f,
    -1f,  1f,
     1f,  1f,
)

internal class AbstraktRenderer : GLSurfaceView.Renderer {

    private var program: ShaderProgram? = null
    private var vaoId = 0
    private var vboId = 0
    private var surfaceWidth  = 1
    private var surfaceHeight = 1
    private var startTimeNs   = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "onSurfaceCreated — building GL resources")
        // After EGL context loss the old GPU objects no longer exist; just reset IDs.
        program = null
        vaoId   = 0
        vboId   = 0
        startTimeNs = 0L

        GLES30.glClearColor(0f, 0f, 0f, 1f)

        program = ShaderProgram(Shaders.TEST_VERT, Shaders.TEST_FRAG)

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

        // attribute location 0 matches layout(location=0) in vertex shader
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        Log.d(TAG, "GL resources ready: vao=$vaoId vbo=$vboId program=${program?.id}")
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        surfaceWidth  = w
        surfaceHeight = h
        GLES30.glViewport(0, 0, w, h)
        Log.d(TAG, "onSurfaceChanged: ${w}x${h}")
    }

    override fun onDrawFrame(gl: GL10?) {
        if (startTimeNs == 0L) startTimeNs = System.nanoTime()
        val timeSec = (System.nanoTime() - startTimeNs) / 1_000_000_000f

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val prog = program ?: return
        prog.use()
        prog.setFloat("u_time", timeSec)
        prog.setVec2("u_resolution", surfaceWidth.toFloat(), surfaceHeight.toFloat())

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }
}
