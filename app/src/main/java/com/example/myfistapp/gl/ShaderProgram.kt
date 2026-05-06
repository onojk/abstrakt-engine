package com.example.myfistapp.gl

import android.opengl.GLES30
import android.util.Log

private const val TAG = "AbstraktGL"

internal class ShaderProgram(vertSrc: String, fragSrc: String) {

    val id: Int = run {
        val vert = compileShader(GLES30.GL_VERTEX_SHADER, vertSrc)
        val frag = compileShader(GLES30.GL_FRAGMENT_SHADER, fragSrc)
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vert)
        GLES30.glAttachShader(prog, frag)
        GLES30.glLinkProgram(prog)
        GLES30.glDeleteShader(vert)
        GLES30.glDeleteShader(frag)
        val status = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == GLES30.GL_FALSE) {
            Log.e(TAG, "Program link failed: ${GLES30.glGetProgramInfoLog(prog)}")
        } else {
            Log.d(TAG, "Shader program linked OK (id=$prog)")
        }
        prog
    }

    fun use() = GLES30.glUseProgram(id)

    fun setFloat(name: String, v: Float) =
        GLES30.glUniform1f(GLES30.glGetUniformLocation(id, name), v)

    fun setVec2(name: String, x: Float, y: Float) =
        GLES30.glUniform2f(GLES30.glGetUniformLocation(id, name), x, y)

    fun setInt(name: String, v: Int) =
        GLES30.glUniform1i(GLES30.glGetUniformLocation(id, name), v)

    fun setVec4(name: String, r: Float, g: Float, b: Float, a: Float) =
        GLES30.glUniform4f(GLES30.glGetUniformLocation(id, name), r, g, b, a)

    // Used for vec2 uniform arrays (e.g. vec2 u_influencer_pos[5]); values is interleaved x,y.
    fun setVec2Array(name: String, values: FloatArray) {
        val loc = GLES30.glGetUniformLocation(id, name)
        if (loc == -1) return
        GLES30.glUniform2fv(loc, values.size / 2, values, 0)
    }

    // Used for float uniform arrays (e.g. float u_bands[8]). Skips silently if optimized out.
    fun setFloatArray(name: String, values: FloatArray) {
        val loc = GLES30.glGetUniformLocation(id, name)
        if (loc == -1) return
        GLES30.glUniform1fv(loc, values.size, values, 0)
    }

    fun setMat4(name: String, value: FloatArray) {
        val loc = GLES30.glGetUniformLocation(id, name)
        if (loc == -1) return
        GLES30.glUniformMatrix4fv(loc, 1, false, value, 0)
    }

    fun delete() = GLES30.glDeleteProgram(id)

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        val label = if (type == GLES30.GL_VERTEX_SHADER) "vertex" else "fragment"
        if (status[0] == GLES30.GL_FALSE) {
            Log.e(TAG, "$label shader compile failed: ${GLES30.glGetShaderInfoLog(shader)}")
        } else {
            Log.d(TAG, "$label shader compiled OK")
        }
        return shader
    }
}
