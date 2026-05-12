package com.onojk.abstrakt.gl

import kotlin.math.cos
import kotlin.math.sin

class SphereShape : Shape {

    override val name = "Sphere"

    override fun buildMesh(): ShapeMesh {
        val rings   = 32
        val sectors = 64
        val radius  = 1.0f

        val vertexCount = (rings + 1) * (sectors + 1)
        val positions   = FloatArray(vertexCount * 3)
        val uvs         = FloatArray(vertexCount * 2)

        var p = 0; var u = 0
        for (r in 0..rings) {
            val phi    = (r.toDouble() / rings) * Math.PI
            val sinPhi = sin(phi).toFloat()
            val cosPhi = cos(phi).toFloat()

            for (s in 0..sectors) {
                val theta    = (s.toDouble() / sectors) * 2.0 * Math.PI
                val sinTheta = sin(theta).toFloat()
                val cosTheta = cos(theta).toFloat()

                positions[p++] = radius * sinPhi * cosTheta  // x
                positions[p++] = radius * cosPhi             // y (vertical axis)
                positions[p++] = radius * sinPhi * sinTheta  // z

                uvs[u++] = s.toFloat() / sectors  // u: 0..1 around longitude
                uvs[u++] = r.toFloat() / rings    // v: 0..1 top to bottom
            }
        }

        // Each quad → 2 CCW triangles (viewed from outside):
        //   tl---tr      T1: tl, bl, tr
        //   |  \  |      T2: tr, bl, br
        //   bl---br
        val indices = ShortArray(rings * sectors * 6)
        var i = 0
        for (r in 0 until rings) {
            for (s in 0 until sectors) {
                val tl = (r       * (sectors + 1) + s    ).toShort()
                val tr = (r       * (sectors + 1) + s + 1).toShort()
                val bl = ((r + 1) * (sectors + 1) + s    ).toShort()
                val br = ((r + 1) * (sectors + 1) + s + 1).toShort()

                indices[i++] = tl; indices[i++] = bl; indices[i++] = tr
                indices[i++] = tr; indices[i++] = bl; indices[i++] = br
            }
        }

        return ShapeMesh(positions, uvs, indices)
    }

    // Tilted axis like Earth — 23.4° off vertical for visual interest.
    override fun rotationAxis(): FloatArray {
        val tilt = Math.toRadians(23.4)
        return floatArrayOf(sin(tilt).toFloat(), cos(tilt).toFloat(), 0f)
    }

    override fun rotationSpeedRadPerSec() = (2.0 * Math.PI / 30.0).toFloat()

    override fun modelScale() = 2.0f

    // Sphere disc at scale=2.0 fills ~50% of shapeFBO width; zoom out to fit frame.
    override fun kaleidoZoom() = 0.88f
}
