package com.onojk.abstrakt.gl

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CaltropShape : Shape {

    override val name = "Caltrop"

    // Parameters matching deck's build_all_shapes(): build_caltrop(8, 1.0, 0.18)
    internal val segments   = 8
    internal val length     = 1.0f
    internal val baseRadius = 0.18f

    override fun buildMesh(): ShapeMesh {
        val axes = arrayOf(
            floatArrayOf( 1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f),
            floatArrayOf( 0f, 1f, 0f), floatArrayOf( 0f,-1f, 0f),
            floatArrayOf( 0f, 0f, 1f), floatArrayOf( 0f, 0f,-1f),
        )

        // 6 cones × (1 tip + segments ring vertices); 6 cones × segments triangles.
        val positions = FloatArray(6 * (1 + segments) * 3)
        val uvs       = FloatArray(6 * (1 + segments) * 2)
        val indices   = ShortArray(6 * segments * 3)

        var p = 0; var u = 0; var idx = 0
        var vertexOffset = 0

        for (axis in axes) {
            val up    = if (abs(axis[1]) < 0.9f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(1f, 0f, 0f)
            val right = caltropNormCross(up, axis)
            val bitan = caltropNormCross(axis, right)

            // Tip vertex
            val tipIndex = vertexOffset
            positions[p++] = axis[0] * length
            positions[p++] = axis[1] * length
            positions[p++] = axis[2] * length
            uvs[u++] = 0.5f; uvs[u++] = 0f

            val ringStart = vertexOffset + 1

            // Base-ring vertices at origin; UV sweeps (s/segments, 1.0).
            for (s in 0 until segments) {
                val a  = (s.toFloat() / segments) * 2f * Math.PI.toFloat()
                val ca = cos(a); val sa = sin(a)
                positions[p++] = right[0] * baseRadius * ca + bitan[0] * baseRadius * sa
                positions[p++] = right[1] * baseRadius * ca + bitan[1] * baseRadius * sa
                positions[p++] = right[2] * baseRadius * ca + bitan[2] * baseRadius * sa
                uvs[u++] = s.toFloat() / segments
                uvs[u++] = 1f
            }

            // Cone triangles: [ring_a, ring_b, tip]
            for (s in 0 until segments) {
                indices[idx++] = (ringStart + s).toShort()
                indices[idx++] = (ringStart + (s + 1) % segments).toShort()
                indices[idx++] = tipIndex.toShort()
            }

            vertexOffset += 1 + segments
        }

        return ShapeMesh(positions, uvs, indices)
    }

    // Body diagonal — same as Cube and Tetrahedron; tumbles through all three axes equally.
    override fun rotationAxis(): FloatArray {
        val n = (1.0 / Math.sqrt(3.0)).toFloat()
        return floatArrayOf(n, n, n)
    }

    override fun rotationSpeedRadPerSec() = (2.0 * Math.PI / 20.0).toFloat()

    override fun modelScale() = 1.2f

    override fun kaleidoZoom() = 0.55f
}

private fun caltropNormCross(a: FloatArray, b: FloatArray): FloatArray {
    val cx = a[1] * b[2] - a[2] * b[1]
    val cy = a[2] * b[0] - a[0] * b[2]
    val cz = a[0] * b[1] - a[1] * b[0]
    val len = sqrt(cx * cx + cy * cy + cz * cz).coerceAtLeast(1e-6f)
    return floatArrayOf(cx / len, cy / len, cz / len)
}
