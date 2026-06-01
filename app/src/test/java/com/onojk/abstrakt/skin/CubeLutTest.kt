package com.onojk.abstrakt.skin

import org.junit.Assert.*
import org.junit.Test

class CubeLutTest {

    private val eps = 1e-4f

    private fun assertNear(expected: Float, actual: Float, msg: String = "") {
        assertEquals(msg, expected, actual, eps)
    }

    @Test
    fun `identity 2x2x2 round-trips`() {
        val cubeText = """
            LUT_3D_SIZE 2
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
        """.trimIndent()

        val result = parseCubeLut("test", cubeText)
        assertTrue(result is CubeLutParseResult.Success)
        val lut = (result as CubeLutParseResult.Success).lut
        assertEquals(2, lut.size)

        // r=0,g=0,b=0 → index 0 → data[0..2] = (0,0,0)
        assertNear(0f, lut.data[0], "data[0]")
        assertNear(0f, lut.data[1], "data[1]")
        assertNear(0f, lut.data[2], "data[2]")

        // r=1,g=0,b=0 → index 1 → data[3..5] = (1,0,0)
        assertNear(1f, lut.data[3], "data[3]")
        assertNear(0f, lut.data[4], "data[4]")
        assertNear(0f, lut.data[5], "data[5]")

        // r=0,g=1,b=0 → index 2 → data[6..8] = (0,1,0)
        assertNear(0f, lut.data[6],  "data[6]")
        assertNear(1f, lut.data[7],  "data[7]")
        assertNear(0f, lut.data[8],  "data[8]")

        // r=0,g=0,b=1 → index 4 → data[12..14] = (0,0,1)
        assertNear(0f, lut.data[12], "data[12]")
        assertNear(0f, lut.data[13], "data[13]")
        assertNear(1f, lut.data[14], "data[14]")
    }

    @Test
    fun `1D LUT rejected`() {
        val text = "LUT_1D_SIZE 17\n0.0 0.0 0.0\n"
        val result = parseCubeLut("test", text)
        assertTrue(result is CubeLutParseResult.Failure)
        val msg = (result as CubeLutParseResult.Failure).reason
        assertTrue("Expected '1D LUTs' in message", msg.contains("1D LUTs"))
    }

    @Test
    fun `comments and TITLE handled`() {
        val cubeText = """
            # This is a comment
            TITLE "My Cool LUT"
            # Another comment
            LUT_3D_SIZE 2
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
        """.trimIndent()

        val result = parseCubeLut("fallback", cubeText)
        assertTrue(result is CubeLutParseResult.Success)
        val lut = (result as CubeLutParseResult.Success).lut
        assertEquals("My Cool LUT", lut.name)
        assertEquals(2, lut.size)
    }

    @Test
    fun `wrong row count rejected`() {
        val cubeText = """
            LUT_3D_SIZE 2
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
        """.trimIndent()

        val result = parseCubeLut("test", cubeText)
        assertTrue(result is CubeLutParseResult.Failure)
        val msg = (result as CubeLutParseResult.Failure).reason
        assertTrue("Expected 'Expected' in message", msg.contains("Expected"))
    }

    @Test
    fun `DOMAIN_MIN MAX scaling applied`() {
        val cubeText = """
            LUT_3D_SIZE 2
            DOMAIN_MIN 0 0 0
            DOMAIN_MAX 255 255 255
            0.0   0.0   0.0
            255.0 0.0   0.0
            0.0   255.0 0.0
            255.0 255.0 0.0
            0.0   0.0   255.0
            255.0 0.0   255.0
            0.0   255.0 255.0
            255.0 255.0 255.0
        """.trimIndent()

        val result = parseCubeLut("test", cubeText)
        assertTrue(result is CubeLutParseResult.Success)
        val lut = (result as CubeLutParseResult.Success).lut
        assertEquals(2, lut.size)

        // r=0,g=0,b=0 → input (0,0,0) → normalized (0,0,0)
        assertNear(0f, lut.data[0], "data[0]")
        assertNear(0f, lut.data[1], "data[1]")
        assertNear(0f, lut.data[2], "data[2]")

        // r=1,g=0,b=0 → input (255,0,0) → normalized (1,0,0)
        assertNear(1f, lut.data[3], "data[3]")
        assertNear(0f, lut.data[4], "data[4]")
        assertNear(0f, lut.data[5], "data[5]")

        // r=0,g=0,b=1 → index 4 → input (0,0,255) → normalized (0,0,1)
        assertNear(0f, lut.data[12], "data[12]")
        assertNear(0f, lut.data[13], "data[13]")
        assertNear(1f, lut.data[14], "data[14]")
    }

    @Test
    fun `malformed data line skipped gracefully`() {
        val cubeText = """
            LUT_3D_SIZE 2
            not_a_number foo bar
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
        """.trimIndent()

        val result = parseCubeLut("test", cubeText)
        assertTrue(result is CubeLutParseResult.Success)
        val lut = (result as CubeLutParseResult.Success).lut
        assertEquals(2, lut.size)
        assertNear(0f, lut.data[0])
        assertNear(1f, lut.data[3])
    }

    @Test
    fun `stream variant works`() {
        val cubeText = """
            LUT_3D_SIZE 2
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
        """.trimIndent()

        val stream = cubeText.byteInputStream(Charsets.UTF_8)
        val result = parseCubeLut("stream_test", stream)
        assertTrue(result is CubeLutParseResult.Success)
        val lut = (result as CubeLutParseResult.Success).lut
        assertEquals(2, lut.size)

        // r=0,g=0,b=0 → data[0..2] = (0,0,0)
        assertNear(0f, lut.data[0])
        assertNear(0f, lut.data[1])
        assertNear(0f, lut.data[2])

        // r=0,g=0,b=1 → index 4 → data[12..14] = (0,0,1)
        assertNear(0f, lut.data[12])
        assertNear(0f, lut.data[13])
        assertNear(1f, lut.data[14])
    }
}
