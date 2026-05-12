package com.onojk.abstrakt.gl

class CubeShape : Shape {

    override val name = "Cube"

    override fun buildMesh(): ShapeMesh {
        // Unit cube centered at origin, side length 2.0 (vertices at ±1.0).
        // 6 faces × 4 vertices = 24 vertices with per-face UV so each face shows
        // the full painter texture independently.
        val s = 1.0f

        // Each face: 4 vertices ordered (bottom-left, bottom-right, top-right, top-left).
        // Winding verified outward-CCW for all 6 faces.
        val positions = floatArrayOf(
            // Front face  (z = +s, outward normal +Z)
            -s, -s,  s,    s, -s,  s,    s,  s,  s,   -s,  s,  s,
            // Back face   (z = -s, outward normal -Z)
             s, -s, -s,   -s, -s, -s,   -s,  s, -s,    s,  s, -s,
            // Right face  (x = +s, outward normal +X)
             s, -s,  s,    s, -s, -s,    s,  s, -s,    s,  s,  s,
            // Left face   (x = -s, outward normal -X)
            -s, -s, -s,   -s, -s,  s,   -s,  s,  s,   -s,  s, -s,
            // Top face    (y = +s, outward normal +Y)
            -s,  s,  s,    s,  s,  s,    s,  s, -s,   -s,  s, -s,
            // Bottom face (y = -s, outward normal -Y)
            -s, -s, -s,    s, -s, -s,    s, -s,  s,   -s, -s,  s,
        )

        // Full 0..1 UV on every face — painter stripe appears on each face identically.
        val uvs = FloatArray(48)
        for (face in 0 until 6) {
            val b = face * 8
            uvs[b + 0] = 0f; uvs[b + 1] = 1f  // bottom-left
            uvs[b + 2] = 1f; uvs[b + 3] = 1f  // bottom-right
            uvs[b + 4] = 1f; uvs[b + 5] = 0f  // top-right
            uvs[b + 6] = 0f; uvs[b + 7] = 0f  // top-left
        }

        // 6 faces × 2 triangles × 3 indices = 36 indices.
        val indices = ShortArray(36)
        for (face in 0 until 6) {
            val bv = (face * 4)
            val bi = face * 6
            indices[bi + 0] = bv.toShort()
            indices[bi + 1] = (bv + 1).toShort()
            indices[bi + 2] = (bv + 2).toShort()
            indices[bi + 3] = bv.toShort()
            indices[bi + 4] = (bv + 2).toShort()
            indices[bi + 5] = (bv + 3).toShort()
        }

        return ShapeMesh(positions, uvs, indices)
    }

    // Vertex-to-opposite-vertex (body diagonal) axis — maximally interesting tumble.
    override fun rotationAxis(): FloatArray {
        val n = (1.0 / Math.sqrt(3.0)).toFloat()
        return floatArrayOf(n, n, n)
    }

    override fun rotationSpeedRadPerSec() = (2.0 * Math.PI / 25.0).toFloat()

    // Cube's face sits at distance 1.0 from origin; shrink slightly so the
    // body diagonal (length √3 ≈ 1.73) doesn't clip the shapeFBO edges.
    override fun modelScale() = 1.4f

    override fun kaleidoZoom() = 0.7f
}
