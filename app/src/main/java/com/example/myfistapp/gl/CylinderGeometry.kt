package com.example.myfistapp.gl

import kotlin.math.cos
import kotlin.math.sin

internal object CylinderGeometry {

    const val ANGULAR_SEGMENTS = 64
    const val RADIUS            = 0.5f
    const val HALF_HEIGHT       = 1.0f  // y from -1.0 to +1.0

    // Interleaved layout per vertex: [x, y, z, u, v]
    const val FLOATS_PER_VERTEX = 5
    const val STRIDE_BYTES      = FLOATS_PER_VERTEX * Float.SIZE_BYTES  // 20

    // Strip: for each of the (N+1) columns, emit bottom then top.
    // Total = 2 * (ANGULAR_SEGMENTS + 1) = 130 vertices.
    val VERTEX_COUNT = 2 * (ANGULAR_SEGMENTS + 1)

    val vertices: FloatArray = buildVertices()

    private fun buildVertices(): FloatArray {
        val data = FloatArray(VERTEX_COUNT * FLOATS_PER_VERTEX)
        var idx  = 0
        for (i in 0..ANGULAR_SEGMENTS) {
            val u     = i.toFloat() / ANGULAR_SEGMENTS
            val angle = u * 2.0 * Math.PI
            val x     = (cos(angle) * RADIUS).toFloat()
            val z     = (sin(angle) * RADIUS).toFloat()

            // Bottom vertex (v = 0.0)
            data[idx++] = x;  data[idx++] = -HALF_HEIGHT;  data[idx++] = z
            data[idx++] = u;  data[idx++] = 0.0f

            // Top vertex (v = 1.0)
            data[idx++] = x;  data[idx++] = HALF_HEIGHT;  data[idx++] = z
            data[idx++] = u;  data[idx++] = 1.0f
        }
        return data
    }
}
