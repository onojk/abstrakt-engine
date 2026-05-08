package com.example.myfistapp.gl

class TetrahedronShape : Shape {

    override val name = "Tetrahedron"

    override fun buildMesh(): ShapeMesh {
        // Regular tetrahedron with all 4 vertices on the unit sphere.
        // The four vertices come from alternating corners of a unit cube:
        //   v0=(+1,+1,+1), v1=(-1,-1,+1), v2=(-1,+1,-1), v3=(+1,-1,-1)
        // All edges have equal length 2√2/√3; centroid is at the origin.

        val sqrt3 = Math.sqrt(3.0).toFloat()

        // Normalised (already on unit sphere since |v| = √3 before normalisation)
        val n0 = floatArrayOf( 1f / sqrt3,  1f / sqrt3,  1f / sqrt3)
        val n1 = floatArrayOf(-1f / sqrt3, -1f / sqrt3,  1f / sqrt3)
        val n2 = floatArrayOf(-1f / sqrt3,  1f / sqrt3, -1f / sqrt3)
        val n3 = floatArrayOf( 1f / sqrt3, -1f / sqrt3, -1f / sqrt3)

        // 4 faces × 3 vertices = 12 vertices.  Winding verified outward-CCW:
        //   Face centroid lies on the side of the origin that the normal points toward,
        //   i.e. cross(v1-v0, v2-v0) · face_centroid > 0 for each face.
        val positions = floatArrayOf(
            // Face 0: n0, n2, n1  — outward normal ≈ (-1,+1,+1)/√3
            n0[0], n0[1], n0[2],   n2[0], n2[1], n2[2],   n1[0], n1[1], n1[2],
            // Face 1: n0, n1, n3  — outward normal ≈ (+1,-1,+1)/√3
            n0[0], n0[1], n0[2],   n1[0], n1[1], n1[2],   n3[0], n3[1], n3[2],
            // Face 2: n0, n3, n2  — outward normal ≈ (+1,+1,-1)/√3
            n0[0], n0[1], n0[2],   n3[0], n3[1], n3[2],   n2[0], n2[1], n2[2],
            // Face 3: n1, n2, n3  — outward normal ≈ (-1,-1,-1)/√3
            n1[0], n1[1], n1[2],   n2[0], n2[1], n2[2],   n3[0], n3[1], n3[2],
        )

        // Triangle UV: apex (0.5, 0), bottom-left (0, 1), bottom-right (1, 1).
        // Gives a compact triangular window into the painter texture on every face.
        val uvs = FloatArray(24)
        for (face in 0 until 4) {
            val b = face * 6
            uvs[b + 0] = 0.5f; uvs[b + 1] = 0f
            uvs[b + 2] = 0f;   uvs[b + 3] = 1f
            uvs[b + 4] = 1f;   uvs[b + 5] = 1f
        }

        // 4 faces × 3 indices = 12 sequential indices.
        val indices = ShortArray(12) { it.toShort() }

        return ShapeMesh(positions, uvs, indices)
    }

    // Vertex v0 direction — rotates apex-over-base like a spinning top.
    override fun rotationAxis(): FloatArray {
        val n = (1.0 / Math.sqrt(3.0)).toFloat()
        return floatArrayOf(n, n, n)
    }

    override fun rotationSpeedRadPerSec() = (2.0 * Math.PI / 22.0).toFloat()

    // Circumscribed sphere radius = 1.0; scale up so the shape fills the frame.
    override fun modelScale() = 1.5f

    override fun kaleidoZoom() = 0.65f
}
