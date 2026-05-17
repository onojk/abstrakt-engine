package com.onojk.abstrakt.gl

import com.onojk.abstrakt.ShapeKind

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

    /**
     * Uniform scale applied to the model matrix before rotation.
     * 1.0 = cylinder reference size. Adjust per-shape so each geometry
     * fills the kaleido frame consistently despite differing bounding shapes.
     */
    fun modelScale(): Float

    /**
     * Kaleido sampling zoom. Values < 1.0 zoom in on the shapeFBO center,
     * effectively enlarging the shape in the kaleido output. Use this to
     * compensate when a shape's projection disc doesn't fill the shapeFBO.
     * Cylinder = 1.0 (no zoom). Sphere = 0.5 (2× zoom in; sphere disc at
     * scale=2.0 fills half the FBO — zooming in 2× fills the frame).
     */
    fun kaleidoZoom(): Float = 1.0f
}

data class ShapeMesh(
    val positions: FloatArray,  // N * 3 floats — x, y, z per vertex
    val uvs: FloatArray,        // N * 2 floats — u, v per vertex
    val indices: ShortArray,    // triangle indices for glDrawElements(GL_TRIANGLES)
)

fun shapeFor(kind: ShapeKind): Shape = when (kind) {
    ShapeKind.Cylinder    -> CylinderShape()
    ShapeKind.Sphere      -> SphereShape()
    ShapeKind.Cube        -> CubeShape()
    ShapeKind.Tetrahedron -> TetrahedronShape()
    ShapeKind.Icosahedron -> IcosahedronShape()
    ShapeKind.Urchin      -> UrchinShape()
    ShapeKind.Caltrop     -> CaltropShape()
}
