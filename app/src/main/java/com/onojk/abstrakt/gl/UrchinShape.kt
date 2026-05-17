package com.onojk.abstrakt.gl

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class UrchinShape : Shape {

    override val name = "Urchin"

    // Parameters matching deck's build_all_shapes(): build_urchin(48, 0.45, 1.0, 6)
    internal val spikeCount       = 48
    internal val baseRadius       = 0.45f
    internal val tipRadius        = 1.0f
    internal val baseRingSegments = 6
    internal val sphereLons       = 24  // longitudes of the inner body sphere
    internal val sphereLats       = 16  // latitudes  of the inner body sphere

    override fun buildMesh(): ShapeMesh {
        // ── Index-buffer budget ───────────────────────────────────────────────
        // GL_UNSIGNED_SHORT holds max 65535 unique vertices.
        // Current layout: sphereVerts(24 lons, 16 lats) = 25×17 = 425, plus
        // spikeCount*(1+baseRingSegments) = 48*7 = 336. Total = 761, well below
        // the limit. If spike_count or sphere resolution grow significantly,
        // switch indices to GL_UNSIGNED_INT (Int array).
        val sphereVertCount = (sphereLons + 1) * (sphereLats + 1)
        val sphereIdxCount  = sphereLons * sphereLats * 6
        val spikeVertCount  = spikeCount * (1 + baseRingSegments)
        val spikeIdxCount   = spikeCount * baseRingSegments * 3

        val positions = FloatArray((sphereVertCount + spikeVertCount) * 3)
        val uvs       = FloatArray((sphereVertCount + spikeVertCount) * 2)
        val indices   = ShortArray(sphereIdxCount + spikeIdxCount)

        var p   = 0   // position write cursor
        var u   = 0   // uv write cursor
        var idx = 0   // index write cursor

        // ── Inner sphere body ─────────────────────────────────────────────────
        // Standard lat/lon sphere matching deck's build_sphere(24, 16, base_radius*0.95).
        // UV: u = lon/lons (horizontal wrap), v = lat/lats (0=north, 1=south).
        val bodyRadius = baseRadius * 0.95f
        for (lat in 0..sphereLats) {
            val phi    = lat.toDouble() / sphereLats * Math.PI
            val sinPhi = sin(phi).toFloat()
            val cosPhi = cos(phi).toFloat()
            val vCoord = lat.toFloat() / sphereLats
            for (lon in 0..sphereLons) {
                val theta    = lon.toDouble() / sphereLons * 2.0 * Math.PI
                positions[p++] = sinPhi * cos(theta).toFloat() * bodyRadius
                positions[p++] = cosPhi * bodyRadius
                positions[p++] = sinPhi * sin(theta).toFloat() * bodyRadius
                uvs[u++] = lon.toFloat() / sphereLons
                uvs[u++] = vCoord
            }
        }
        val stride = sphereLons + 1
        for (lat in 0 until sphereLats) {
            for (lon in 0 until sphereLons) {
                val tl = (lat       * stride + lon    ).toShort()
                val tr = (lat       * stride + lon + 1).toShort()
                val bl = ((lat + 1) * stride + lon    ).toShort()
                val br = ((lat + 1) * stride + lon + 1).toShort()
                indices[idx++] = tl; indices[idx++] = bl; indices[idx++] = tr
                indices[idx++] = tr; indices[idx++] = bl; indices[idx++] = br
            }
        }

        // ── Fibonacci-sphere spikes ───────────────────────────────────────────
        // Each spike: 1 tip vertex + baseRingSegments base-ring vertices, then
        // baseRingSegments cone triangles fanning from ring to tip.
        // UV: tip → (0.5, 0); ring vertex s → (s/segments, 1.0).
        val goldenAngle   = (Math.PI * (3.0 - sqrt(5.0))).toFloat()
        val halfBaseAngle = (2.0 * Math.PI / spikeCount * 0.35).toFloat()
        val halfA         = sin(halfBaseAngle)
        val along         = cos(halfBaseAngle)

        var vertexOffset = sphereVertCount

        for (i in 0 until spikeCount) {
            val y   = 1f - (i.toFloat() / (spikeCount - 1).coerceAtLeast(1)) * 2f
            val rXZ = sqrt((1f - y * y).coerceAtLeast(0f))
            val theta = goldenAngle * i
            val dir = floatArrayOf(rXZ * cos(theta), y, rXZ * sin(theta))

            val up    = if (abs(dir[1]) < 0.9f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(1f, 0f, 0f)
            val right = normCross(up, dir)
            val bitan = normCross(dir, right)

            // Tip vertex
            val tipIndex = vertexOffset
            positions[p++] = dir[0] * tipRadius
            positions[p++] = dir[1] * tipRadius
            positions[p++] = dir[2] * tipRadius
            uvs[u++] = 0.5f; uvs[u++] = 0f

            val ringStart = vertexOffset + 1

            // Base-ring vertices
            for (s in 0 until baseRingSegments) {
                val a  = (s.toFloat() / baseRingSegments) * 2f * Math.PI.toFloat()
                val ca = cos(a); val sa = sin(a)
                val radialX = right[0] * ca + bitan[0] * sa
                val radialY = right[1] * ca + bitan[1] * sa
                val radialZ = right[2] * ca + bitan[2] * sa
                positions[p++] = (dir[0] * along + radialX * halfA) * baseRadius
                positions[p++] = (dir[1] * along + radialY * halfA) * baseRadius
                positions[p++] = (dir[2] * along + radialZ * halfA) * baseRadius
                uvs[u++] = s.toFloat() / baseRingSegments
                uvs[u++] = 1f
            }

            // Cone triangles: [ring_a, ring_b, tip]
            for (s in 0 until baseRingSegments) {
                indices[idx++] = (ringStart + s).toShort()
                indices[idx++] = (ringStart + (s + 1) % baseRingSegments).toShort()
                indices[idx++] = tipIndex.toShort()
            }

            vertexOffset += 1 + baseRingSegments
        }

        return ShapeMesh(positions, uvs, indices)
    }

    // 17° tilt from vertical — slight lean gives visual interest without symmetry.
    override fun rotationAxis(): FloatArray {
        val tilt = Math.toRadians(17.0)
        return floatArrayOf(sin(tilt).toFloat(), cos(tilt).toFloat(), 0f)
    }

    override fun rotationSpeedRadPerSec() = (2.0 * Math.PI / 28.0).toFloat()

    override fun modelScale() = 1.3f

    override fun kaleidoZoom() = 0.8f
}

private fun normCross(a: FloatArray, b: FloatArray): FloatArray {
    val cx = a[1] * b[2] - a[2] * b[1]
    val cy = a[2] * b[0] - a[0] * b[2]
    val cz = a[0] * b[1] - a[1] * b[0]
    val len = sqrt(cx * cx + cy * cy + cz * cz).coerceAtLeast(1e-6f)
    return floatArrayOf(cx / len, cy / len, cz / len)
}
