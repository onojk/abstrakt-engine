package com.example.myfistapp.gl

/**
 * A 3D shape that gets the painter FBO mapped onto its surface,
 * spun on a "skewer" axis, then fed into the kaleido pass.
 */
interface Shape {
    /** Display name shown in UI. */
    val name: String

    /**
     * Build the mesh once during onSurfaceCreated.
     * Returns vertex positions (vec3), texture coords (vec2), and indices.
     */
    fun buildMesh(): ShapeMesh

    /**
     * Unit vector — the axis around which this shape rotates.
     * Cylinder: (0, 1, 0) for vertical axis.
     */
    fun rotationAxis(): FloatArray  // size 3, normalized

    /**
     * The rotation rate in radians/second.
     * Cylinder: 2π/30 (one full rotation every 30 seconds).
     */
    fun rotationSpeedRadPerSec(): Float
}

data class ShapeMesh(
    val positions: FloatArray,  // N * 3 floats — x, y, z per vertex
    val uvs: FloatArray,        // N * 2 floats — u, v per vertex
    val indices: ShortArray,    // triangle indices for glDrawElements(GL_TRIANGLES)
)
