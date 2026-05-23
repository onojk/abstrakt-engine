package com.onojk.abstrakt.skin

object BuiltinLuts {
    data class Entry(val id: String, val label: String)

    /** Fallback list used by PartyEngine (no AssetManager access there). */
    val all: List<Entry> = listOf(
        Entry("identity",    "Original"),
        Entry("warm",        "Warm"),
        Entry("cool",        "Cool"),
        Entry("vivid",       "Vivid"),
        Entry("bw",          "B&W"),
        Entry("sepia",       "Sepia"),
        Entry("teal_orange", "Teal Orange"),
        Entry("contrast",    "Contrast"),
    )

    /** "teal_orange" → "Teal Orange", "bw" → "BW", "cool" → "Cool" */
    fun prettifyName(id: String): String =
        id.split('_').joinToString(" ") { word ->
            if (word.lowercase() == "bw") "B&W"
            else word.replaceFirstChar { it.uppercase() }
        }

    /** Enumerate assets/luts/ at runtime — adding/removing .cube files is automatic. */
    fun listFromAssets(assets: android.content.res.AssetManager): List<Entry> =
        (assets.list("luts") ?: emptyArray())
            .filter { it.endsWith(".cube") }
            .sorted()
            .map { filename ->
                val id = filename.removeSuffix(".cube")
                Entry(id, prettifyName(id))
            }

    // Fast in-memory approximations for PartyEngine (no IO needed for live randomization).
    fun generate(id: String, n: Int = 17): CubeLut {
        val data = FloatArray(n * n * n * 3)
        for (bi in 0 until n) {
            for (gi in 0 until n) {
                for (ri in 0 until n) {
                    val r = ri.toFloat() / (n - 1)
                    val g = gi.toFloat() / (n - 1)
                    val b = bi.toFloat() / (n - 1)

                    val (ro, go, bo) = when (id) {
                        "identity" -> Triple(r, g, b)
                        "warm" -> Triple(
                            (r * 1.18f + 0.08f).coerceIn(0f, 1f),
                            g,
                            (b * 0.72f).coerceIn(0f, 1f),
                        )
                        "cool" -> {
                            val luma = r * 0.299f + g * 0.587f + b * 0.114f
                            val rr = (r * 0.72f).coerceIn(0f, 1f)
                            val gg = g
                            val bb = (b * 1.18f + 0.08f).coerceIn(0f, 1f)
                            Triple(
                                rr + (luma - rr) * 0.15f,
                                gg + (luma - gg) * 0.15f,
                                bb + (luma - bb) * 0.15f,
                            )
                        }
                        "vivid" -> {
                            val luma = r * 0.299f + g * 0.587f + b * 0.114f
                            Triple(
                                (luma + (r - luma) * 1.6f).coerceIn(0f, 1f),
                                (luma + (g - luma) * 1.6f).coerceIn(0f, 1f),
                                (luma + (b - luma) * 1.6f).coerceIn(0f, 1f),
                            )
                        }
                        "bw" -> {
                            val luma = r * 0.299f + g * 0.587f + b * 0.114f
                            Triple(luma, luma, luma)
                        }
                        "sepia" -> {
                            val luma = r * 0.299f + g * 0.587f + b * 0.114f
                            Triple(
                                (luma * 1.10f + 0.12f).coerceIn(0f, 1f),
                                (luma * 0.82f + 0.02f).coerceIn(0f, 1f),
                                (luma * 0.55f).coerceIn(0f, 1f),
                            )
                        }
                        "teal_orange" -> {
                            val luma = r * 0.299f + g * 0.587f + b * 0.114f
                            val sr = 0.0f + (0.65f - 0.0f) * luma
                            val sg = 0.45f + (0.42f - 0.45f) * luma
                            val sb = 0.55f + (0.00f - 0.55f) * luma
                            Triple(
                                (r + (sr - r) * 0.55f).coerceIn(0f, 1f),
                                (g + (sg - g) * 0.55f).coerceIn(0f, 1f),
                                (b + (sb - b) * 0.55f).coerceIn(0f, 1f),
                            )
                        }
                        "contrast" -> {
                            fun boost(x: Float) = ((x - 0.5f) * 1.5f + 0.5f).coerceIn(0f, 1f)
                            Triple(boost(r), boost(g), boost(b))
                        }
                        else -> Triple(r, g, b)
                    }

                    val idx = (ri + gi * n + bi * n * n) * 3
                    data[idx + 0] = ro
                    data[idx + 1] = go
                    data[idx + 2] = bo
                }
            }
        }
        return CubeLut(name = id, size = n, data = data)
    }
}
