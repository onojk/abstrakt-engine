package com.onojk.abstrakt

import org.junit.Assert.*
import org.junit.Test

class ExportConfigTest {

    // ── Deck preset parity ────────────────────────────────────────────────────

    @Test
    fun `all four deck resolution presets are present with exact dimensions`() {
        val entries = ExportResolution.entries
        assertTrue("480p (854×480) must be present",  entries.any { it.width == 854  && it.height == 480  })
        assertTrue("720p (1280×720) must be present", entries.any { it.width == 1280 && it.height == 720  })
        assertTrue("1080p (1920×1080) must be present", entries.any { it.width == 1920 && it.height == 1080 })
        assertTrue("4K (3840×2160) must be present",  entries.any { it.width == 3840 && it.height == 2160 })
    }

    @Test
    fun `SD_480P is the lowest resolution entry`() {
        val entries = ExportResolution.entries
        val first = entries.first()
        assertEquals("SD_480P must be first (lowest res)", ExportResolution.SD_480P, first)
        assertEquals(854,  first.width)
        assertEquals(480, first.height)
    }

    @Test
    fun `entries are in ascending resolution order`() {
        val entries = ExportResolution.entries
        for (i in 0 until entries.size - 1) {
            val a = entries[i]
            val b = entries[i + 1]
            val pixelsA = a.width.toLong() * a.height
            val pixelsB = b.width.toLong() * b.height
            assertTrue(
                "${a.name} (${pixelsA}px) must precede ${b.name} (${pixelsB}px)",
                pixelsA < pixelsB,
            )
        }
    }

    // ── StorageEstimator parity ───────────────────────────────────────────────

    @Test
    fun `StorageEstimator produces positive estimates for all presets`() {
        ExportResolution.entries.forEach { res ->
            val bytes = StorageEstimator.estimateBytes(res.bitrate, 60.0, false)
            assertTrue("${res.name} (bitrate=${res.bitrate}) must produce a positive size estimate; got $bytes",
                bytes > 0)
        }
    }

    @Test
    fun `SD_480P estimate is smaller than HD_720P estimate`() {
        val est480  = StorageEstimator.estimateBytes(ExportResolution.SD_480P.bitrate,  60.0, false)
        val est720  = StorageEstimator.estimateBytes(ExportResolution.HD_720P.bitrate,  60.0, false)
        val est1080 = StorageEstimator.estimateBytes(ExportResolution.FHD_1080P.bitrate, 60.0, false)
        val est4k   = StorageEstimator.estimateBytes(ExportResolution.UHD_4K.bitrate,   60.0, false)
        assertTrue("480p estimate must be < 720p",  est480  < est720)
        assertTrue("720p estimate must be < 1080p", est720  < est1080)
        assertTrue("1080p estimate must be < 4K",   est1080 < est4k)
    }

    // ── Bitrate sanity ────────────────────────────────────────────────────────

    @Test
    fun `all presets have positive bitrates`() {
        ExportResolution.entries.forEach { res ->
            assertTrue("${res.name} must have a positive bitrate", res.bitrate > 0)
        }
    }
}
