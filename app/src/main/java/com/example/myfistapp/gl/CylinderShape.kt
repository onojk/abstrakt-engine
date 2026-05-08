package com.example.myfistapp.gl

import kotlin.math.cos
import kotlin.math.sin

class CylinderShape : Shape {

    override val name = "Cylinder"

    override fun buildMesh(): ShapeMesh {
        val segments = 64
        val radius   = 0.5f
        val halfH    = 1.0f

        // 2*(segments+1) vertices: for each column, bottom then top.
        val vertexCount = 2 * (segments + 1)
        val positions   = FloatArray(vertexCount * 3)
        val uvs         = FloatArray(vertexCount * 2)

        var pi = 0; var ui = 0
        for (i in 0..segments) {
            val u     = i.toFloat() / segments
            val angle = u * 2.0 * Math.PI
            val x     = (cos(angle) * radius).toFloat()
            val z     = (sin(angle) * radius).toFloat()

            // Bottom vertex (v = 0)
            positions[pi++] = x;  positions[pi++] = -halfH;  positions[pi++] = z
            uvs[ui++] = u;        uvs[ui++] = 0f

            // Top vertex (v = 1)
            positions[pi++] = x;  positions[pi++] = halfH;   positions[pi++] = z
            uvs[ui++] = u;        uvs[ui++] = 1f
        }

        // Indexed triangles matching GL_TRIANGLE_STRIP winding exactly.
        // Strip quad i → v[2i], v[2i+1], v[2(i+1)], v[2(i+1)+1]
        // Strip T_A (even): v[2i], v[2i+1], v[2(i+1)]         — CCW from outside ✓
        // Strip T_B (odd):  v[2(i+1)], v[2i+1], v[2(i+1)+1]  — CCW from outside ✓
        val indices = ShortArray(segments * 6)
        var ii = 0
        for (i in 0 until segments) {
            val b  = (2 * i).toShort()
            val t  = (2 * i + 1).toShort()
            val nb = (2 * (i + 1)).toShort()
            val nt = (2 * (i + 1) + 1).toShort()
            indices[ii++] = b;   indices[ii++] = t;  indices[ii++] = nb   // T_A
            indices[ii++] = nb;  indices[ii++] = t;  indices[ii++] = nt   // T_B
        }

        return ShapeMesh(positions, uvs, indices)
    }

    override fun rotationAxis() = floatArrayOf(0f, 1f, 0f)

    override fun rotationSpeedRadPerSec() = (2.0 * Math.PI / 30.0).toFloat()

    override fun modelScale() = 1.0f

    override fun kaleidoZoom() = 0.6f
}
