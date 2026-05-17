package com.onojk.abstrakt.audio

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for PitchAnalyzer. All tests are pure Kotlin with no Android dependencies.
 *
 * Assertions mirror deck's pitch.rs unit tests exactly (same synthetic inputs, same
 * thresholds). The `makeChromaForKey` helper also mirrors deck's test helper of the same name.
 *
 * KeyTracker.lockedKey requires 3 consistent estimates above the 0.70 confidence threshold
 * (Android addition — deck has no debouncing). 500 chunks at 0.04 s/chunk = 20 s, which
 * gives the 4 s history window time to fill and the 3-hit gate time to trip.
 */
class PitchAnalyzerTest {

    // Mirrors deck's make_chroma_for_key: builds a chroma that, when fed to
    // pearsonRotated(c, profile, rot), returns exactly 1.0.
    private fun makeChromaForKey(rot: Int, isMajor: Boolean): FloatArray {
        val profile = if (isMajor) KS_MAJOR else KS_MINOR
        val c = FloatArray(12)
        for (j in 0..11) c[j] = profile[(j + 12 - rot) % 12]
        return c
    }

    // ── pearsonRotated ────────────────────────────────────────────────────────

    // Mirrors deck test: pearson_self_correlation_is_one
    @Test
    fun `pearsonRotated self-correlation of KS_MAJOR at rot=0 is 1_0`() {
        val r = pearsonRotated(KS_MAJOR, KS_MAJOR, 0)
        assertEquals("self-correlation must be 1.0", 1f, r, 1e-5f)
    }

    // Mirrors deck test: pearson_profile_derived_chroma_matches_at_correct_rotation (F# major)
    @Test
    fun `pearsonRotated finds rot=6 as best match for F# major chroma`() {
        val c       = makeChromaForKey(6, true)
        val rFsharp = pearsonRotated(c, KS_MAJOR, 6)
        val rC      = pearsonRotated(c, KS_MAJOR, 0)
        assertEquals("F# major chroma vs KS_MAJOR at rot=6 must be 1.0", 1f, rFsharp, 1e-5f)
        assertTrue("rot=6 must outscore rot=0 for F# major input; rot6=$rFsharp rot0=$rC",
            rFsharp > rC)
    }

    // Mirrors deck test: c_major_scale_tones_outscores_all_other_keys
    @Test
    fun `C major scale tones outscore C minor and F# major`() {
        val cScale = floatArrayOf(1f,0f,1f,0f,1f,1f,0f,1f,0f,1f,0f,1f)
        val rCMaj  = pearsonRotated(cScale, KS_MAJOR, 0)
        val rCMin  = pearsonRotated(cScale, KS_MINOR, 0)
        val rFsMaj = pearsonRotated(cScale, KS_MAJOR, 6)
        assertTrue("C scale should match major over minor; maj=$rCMaj min=$rCMin", rCMaj > rCMin)
        assertTrue("C scale should match C major over F# major; C=$rCMaj F#=$rFsMaj", rCMaj > rFsMaj)
        assertTrue("C scale vs C major profile must be > 0.7; got $rCMaj", rCMaj > 0.7f)
    }

    // ── normalizeProfile ──────────────────────────────────────────────────────

    @Test
    fun `normalizeProfile returns true and unit L2 norm for non-zero input`() {
        val v  = floatArrayOf(3f, 4f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val ok = normalizeProfile(v)
        assertTrue("normalizeProfile should return true for non-zero input", ok)
        var sumSq = 0f; for (x in v) sumSq += x * x
        assertEquals("L2 norm of result must be 1.0", 1f, kotlin.math.sqrt(sumSq), 1e-5f)
    }

    @Test
    fun `normalizeProfile returns false and leaves vector unchanged for all-zero input`() {
        val v  = FloatArray(12)
        val ok = normalizeProfile(v)
        assertFalse("normalizeProfile should return false for all-zero input", ok)
        assertTrue("all-zero input should remain all-zero", v.all { it == 0f })
    }

    // ── KeyTracker ────────────────────────────────────────────────────────────

    // Mirrors deck test: key_tracker_detects_c_major
    @Test
    fun `KeyTracker locks to C major from synthetic C major chroma`() {
        val t      = KeyTracker(0.04f)
        val chroma = makeChromaForKey(0, true)
        repeat(500) { t.processChunk(chroma) }
        val key = t.lockedKey
        assertNotNull("KeyTracker should lock to a key by 500 chunks", key)
        assertEquals("expected root C (0), got ${key!!.first}", 0, key.first)
        assertTrue("expected major mode", key.second)
        assertTrue("expected confidence > 0.9, got ${t.confidence}", t.confidence > 0.9f)
    }

    // Mirrors deck test: key_tracker_detects_a_minor
    @Test
    fun `KeyTracker locks to A minor from synthetic A minor chroma`() {
        val t      = KeyTracker(0.04f)
        val chroma = makeChromaForKey(9, false)
        repeat(500) { t.processChunk(chroma) }
        val key = t.lockedKey
        assertNotNull("KeyTracker should lock to a key by 500 chunks", key)
        assertEquals("expected root A (9), got ${key!!.first}", 9, key.first)
        assertFalse("expected minor mode", key.second)
        assertTrue("expected confidence > 0.9, got ${t.confidence}", t.confidence > 0.9f)
    }

    // Mirrors deck test: key_tracker_silence_leaves_confidence_zero
    @Test
    fun `KeyTracker silence never produces a lock and leaves confidence at 0`() {
        val t = KeyTracker(0.04f)
        repeat(200) { t.processChunk(FloatArray(12)) }
        assertNull("silence must never produce a key lock", t.lockedKey)
        assertEquals("silence must leave confidence at 0.0", 0f, t.confidence, 1e-6f)
    }

    @Test
    fun `KeyTracker processChunk returns true exactly once when key first locks`() {
        val t      = KeyTracker(0.04f)
        val chroma = makeChromaForKey(0, true)
        var locks  = 0
        repeat(500) { if (t.processChunk(chroma)) locks++ }
        assertEquals("processChunk should return true exactly once (initial lock)", 1, locks)
    }
}
