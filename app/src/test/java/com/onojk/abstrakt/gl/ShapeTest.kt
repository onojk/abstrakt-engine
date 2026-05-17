package com.onojk.abstrakt.gl

import org.junit.Assert.*
import org.junit.Test

class ShapeTest {

    // ── Icosahedron ───────────────────────────────────────────────────────────

    @Test
    fun `Icosahedron has exactly 60 vertices and 60 indices`() {
        // Geometry is defined by a hard-coded 20-face table; per-face duplication
        // gives exactly 20 × 3 = 60 vertices and sequential indices.
        val mesh = IcosahedronShape().buildMesh()
        assertEquals(60, mesh.positions.size / 3)
        assertEquals(60, mesh.uvs.size / 2)
        assertEquals(60, mesh.indices.size)
    }

    @Test
    fun `Icosahedron indices are sequential 0 to 59`() {
        val mesh = IcosahedronShape().buildMesh()
        for (i in 0 until 60) assertEquals(i.toShort(), mesh.indices[i])
    }

    @Test
    fun `Icosahedron has no NaN or Inf in positions or UVs`() {
        val mesh = IcosahedronShape().buildMesh()
        assertFalse(mesh.positions.any { it.isNaN() || it.isInfinite() })
        assertFalse(mesh.uvs.any      { it.isNaN() || it.isInfinite() })
    }

    @Test
    fun `Icosahedron UV values are in range 0 to 1`() {
        val mesh = IcosahedronShape().buildMesh()
        assertTrue(mesh.uvs.all { it in 0f..1f })
    }

    @Test
    fun `Icosahedron buildMesh is deterministic`() {
        val a = IcosahedronShape().buildMesh()
        val b = IcosahedronShape().buildMesh()
        assertArrayEquals(a.positions, b.positions, 0f)
        assertArrayEquals(a.uvs,       b.uvs,       0f)
        assertArrayEquals(a.indices,   b.indices)
    }

    // ── Urchin ────────────────────────────────────────────────────────────────

    @Test
    fun `Urchin has parameter-derived vertex and index counts`() {
        val shape = UrchinShape()
        val mesh  = shape.buildMesh()

        // Formula: sphereVerts(lons, lats) + spikeCount*(1+baseRingSegments)
        val expectedSphereVerts = (shape.sphereLons + 1) * (shape.sphereLats + 1)
        val expectedSpikeVerts  = shape.spikeCount * (1 + shape.baseRingSegments)
        val expectedVerts       = expectedSphereVerts + expectedSpikeVerts

        // Formula: sphereLons*sphereLats*6 + spikeCount*baseRingSegments*3
        val expectedSphereIdxs = shape.sphereLons * shape.sphereLats * 6
        val expectedSpikeIdxs  = shape.spikeCount * shape.baseRingSegments * 3
        val expectedIdxs       = expectedSphereIdxs + expectedSpikeIdxs

        assertEquals(expectedVerts, mesh.positions.size / 3)
        assertEquals(expectedVerts, mesh.uvs.size / 2)
        assertEquals(expectedIdxs,  mesh.indices.size)
    }

    @Test
    fun `Urchin all indices reference valid vertices`() {
        val mesh      = UrchinShape().buildMesh()
        val vertCount = mesh.positions.size / 3
        assertTrue(mesh.indices.all { it.toInt() in 0 until vertCount })
    }

    @Test
    fun `Urchin has no NaN or Inf in positions or UVs`() {
        val mesh = UrchinShape().buildMesh()
        assertFalse(mesh.positions.any { it.isNaN() || it.isInfinite() })
        assertFalse(mesh.uvs.any      { it.isNaN() || it.isInfinite() })
    }

    @Test
    fun `Urchin buildMesh is deterministic`() {
        val a = UrchinShape().buildMesh()
        val b = UrchinShape().buildMesh()
        assertArrayEquals(a.positions, b.positions, 0f)
        assertArrayEquals(a.uvs,       b.uvs,       0f)
        assertArrayEquals(a.indices,   b.indices)
    }

    // ── Caltrop ───────────────────────────────────────────────────────────────

    @Test
    fun `Caltrop has parameter-derived vertex and index counts`() {
        val shape = CaltropShape()
        val mesh  = shape.buildMesh()

        // Formula: 6 axes × (1 tip + segments ring vertices)
        val expectedVerts = 6 * (1 + shape.segments)
        // Formula: 6 axes × segments triangles × 3 indices
        val expectedIdxs  = 6 * shape.segments * 3

        assertEquals(expectedVerts, mesh.positions.size / 3)
        assertEquals(expectedVerts, mesh.uvs.size / 2)
        assertEquals(expectedIdxs,  mesh.indices.size)
    }

    @Test
    fun `Caltrop all indices reference valid vertices`() {
        val mesh      = CaltropShape().buildMesh()
        val vertCount = mesh.positions.size / 3
        assertTrue(mesh.indices.all { it.toInt() in 0 until vertCount })
    }

    @Test
    fun `Caltrop has no NaN or Inf in positions or UVs`() {
        val mesh = CaltropShape().buildMesh()
        assertFalse(mesh.positions.any { it.isNaN() || it.isInfinite() })
        assertFalse(mesh.uvs.any      { it.isNaN() || it.isInfinite() })
    }

    @Test
    fun `Caltrop UV values are in range 0 to 1`() {
        val mesh = CaltropShape().buildMesh()
        assertTrue(mesh.uvs.all { it in 0f..1f })
    }

    @Test
    fun `Caltrop buildMesh is deterministic`() {
        val a = CaltropShape().buildMesh()
        val b = CaltropShape().buildMesh()
        assertArrayEquals(a.positions, b.positions, 0f)
        assertArrayEquals(a.uvs,       b.uvs,       0f)
        assertArrayEquals(a.indices,   b.indices)
    }
}
