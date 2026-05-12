package com.onojk.abstrakt.gl

import java.util.Random

private const val NUM_BARS   = 32
private const val NUM_BANDS  = 8
private const val NUM_LAYERS = 4
private const val RING_SIZE  = 16   // covers max access: LAG[3](6) + ghost_age(5) = tick 11
private const val RING_TICKS = 12   // how many ticks we export to the shader
private const val HIST_UPDATE_SECS = 0.08f

// Layer read offsets in ring-buffer ticks (1 tick ≈ 80ms).
private val LAG_TICKS = intArrayOf(0, 1, 3, 6)

// Seeds for 4 independent Fisher-Yates shuffles — different columns light up per layer.
private val SCRAMBLE_SEEDS = longArrayOf(42L, 137L, 293L, 511L)

internal class DriftState {

    // 4 scrambles packed as float[128]: scrambleF[layer*32 + col] = band index (0..7).
    val scrambleF: FloatArray = run {
        val combined = FloatArray(NUM_LAYERS * NUM_BARS)
        for (l in 0 until NUM_LAYERS) {
            val arr = IntArray(NUM_BARS) { it % NUM_BANDS }
            val rng = Random(SCRAMBLE_SEEDS[l])
            for (i in NUM_BARS - 1 downTo 1) {
                val j = rng.nextInt(i + 1)
                val tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp
            }
            for (col in 0 until NUM_BARS) combined[l * NUM_BARS + col] = arr[col].toFloat()
        }
        combined
    }

    // Ring buffer: bandRing[tick * NUM_BANDS + band], wraps at RING_SIZE.
    private val bandRing = FloatArray(RING_SIZE * NUM_BANDS)
    private var writeHead = 0
    private var lastUpdateTimeSec = 0f

    // Pre-linearized export: slot 0 = newest tick, slot 11 = 11 ticks ago.
    // Passed as u_band_ring[96] to the shader.
    private val bandRingExport = FloatArray(RING_TICKS * NUM_BANDS)

    fun update(timeSec: Float, bands: FloatArray) {
        if (timeSec - lastUpdateTimeSec < HIST_UPDATE_SECS) return
        lastUpdateTimeSec = timeSec

        // Advance write head and record current bands.
        writeHead = (writeHead + 1) % RING_SIZE
        for (b in 0 until NUM_BANDS) bandRing[writeHead * NUM_BANDS + b] = bands[b]

        // Export RING_TICKS slots newest-first so the shader can index with
        // u_band_ring[(lag + ghostAge) * NUM_BANDS + bandIdx].
        for (t in 0 until RING_TICKS) {
            val src = ((writeHead - t) + RING_SIZE * 2) % RING_SIZE
            for (b in 0 until NUM_BANDS) {
                bandRingExport[t * NUM_BANDS + b] = bandRing[src * NUM_BANDS + b]
            }
        }
    }

    fun applyToProgram(program: ShaderProgram) {
        program.setFloatArray("u_band_ring", bandRingExport)
        program.setFloatArray("u_scramble",  scrambleF)
    }
}
