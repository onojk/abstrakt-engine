package com.onojk.abstrakt.gl

import kotlin.math.sqrt

class IcosahedronShape : Shape {

    override val name = "Icosahedron"

    override fun buildMesh(): ShapeMesh {
        // Golden-ratio icosahedron: 12 vertices on the unit sphere, 20 triangular faces.
        // Ported directly from deck's build_icosahedron(scale=0.7).
        val scale = 0.7f
        val t = (1f + sqrt(5f)) * 0.5f  // golden ratio ≈ 1.618
        val n = sqrt(1f + t * t)
        val a = scale / n
        val b = scale * t / n

        // 12 base vertices — three mutually perpendicular golden rectangles.
        val raw = Array(12) { FloatArray(3) }
        raw[ 0] = floatArrayOf(-a,  b, 0f);  raw[ 1] = floatArrayOf( a,  b, 0f)
        raw[ 2] = floatArrayOf(-a, -b, 0f);  raw[ 3] = floatArrayOf( a, -b, 0f)
        raw[ 4] = floatArrayOf(0f, -a,  b);  raw[ 5] = floatArrayOf(0f,  a,  b)
        raw[ 6] = floatArrayOf(0f, -a, -b);  raw[ 7] = floatArrayOf(0f,  a, -b)
        raw[ 8] = floatArrayOf( b, 0f, -a);  raw[ 9] = floatArrayOf( b, 0f,  a)
        raw[10] = floatArrayOf(-b, 0f, -a);  raw[11] = floatArrayOf(-b, 0f,  a)

        // 20 faces — same table as deck's build_icosahedron.
        val faces = arrayOf(
            intArrayOf( 0,11, 5), intArrayOf( 0, 5, 1), intArrayOf( 0, 1, 7), intArrayOf( 0, 7,10), intArrayOf( 0,10,11),
            intArrayOf( 1, 5, 9), intArrayOf( 5,11, 4), intArrayOf(11,10, 2), intArrayOf(10, 7, 6), intArrayOf( 7, 1, 8),
            intArrayOf( 3, 9, 4), intArrayOf( 3, 4, 2), intArrayOf( 3, 2, 6), intArrayOf( 3, 6, 8), intArrayOf( 3, 8, 9),
            intArrayOf( 4, 9, 5), intArrayOf( 2, 4,11), intArrayOf( 6, 2,10), intArrayOf( 8, 6, 7), intArrayOf( 9, 8, 1),
        )

        // Per-face UV: same triangular window as Tetrahedron.
        // vertex 0 → bottom-left (0,1), vertex 1 → bottom-right (1,1), vertex 2 → top-center (0.5,0).
        val faceUv = floatArrayOf(0f, 1f,  1f, 1f,  0.5f, 0f)

        // 20 faces × 3 vertices = 60 vertices (per-face duplication; no shared vertices).
        val positions = FloatArray(60 * 3)
        val uvs       = FloatArray(60 * 2)
        val indices   = ShortArray(60) { it.toShort() }

        for ((fi, face) in faces.withIndex()) {
            for (i in 0..2) {
                val vi    = face[i]
                val pBase = (fi * 3 + i) * 3
                positions[pBase]     = raw[vi][0]
                positions[pBase + 1] = raw[vi][1]
                positions[pBase + 2] = raw[vi][2]
                val uBase = (fi * 3 + i) * 2
                uvs[uBase]     = faceUv[i * 2]
                uvs[uBase + 1] = faceUv[i * 2 + 1]
            }
        }

        return ShapeMesh(positions, uvs, indices)
    }

    // Lopsided rotation axis — visually more interesting than a symmetry axis.
    // Deck: n = 1/√6, axis = [n, 2n, n], which is unit: ‖[n,2n,n]‖ = n√6 = 1.
    override fun rotationAxis(): FloatArray {
        val n = (1.0 / Math.sqrt(6.0)).toFloat()
        return floatArrayOf(n, 2f * n, n)
    }

    override fun rotationSpeedRadPerSec() = (2.0 * Math.PI / 26.0).toFloat()

    override fun modelScale() = 1.6f

    override fun kaleidoZoom() = 0.75f
}
