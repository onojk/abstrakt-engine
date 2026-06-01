package com.onojk.abstrakt.skin

data class CubeLut(
    val name: String,
    val size: Int,
    val data: FloatArray,  // N*N*N*3, R-fastest order: index = (r + g*N + b*N*N)*3
)

sealed class CubeLutParseResult {
    data class Success(val lut: CubeLut) : CubeLutParseResult()
    data class Failure(val reason: String) : CubeLutParseResult()
}

fun parseCubeLut(name: String, text: String): CubeLutParseResult {
    var lutName = name
    var size = -1
    var domainMinR = 0f; var domainMinG = 0f; var domainMinB = 0f
    var domainMaxR = 1f; var domainMaxG = 1f; var domainMaxB = 1f
    val dataLines = mutableListOf<FloatArray>()

    for (rawLine in text.lines()) {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) continue

        when {
            line.startsWith("TITLE") -> {
                // Extract title, strip surrounding quotes if present
                val rest = line.removePrefix("TITLE").trim()
                lutName = rest.trim('"')
            }
            line.startsWith("LUT_1D_SIZE") -> {
                return CubeLutParseResult.Failure("1D LUTs are not supported")
            }
            line.startsWith("LUT_3D_SIZE") -> {
                val parts = line.split(Regex("\\s+"))
                size = parts.getOrNull(1)?.toIntOrNull() ?: -1
            }
            line.startsWith("DOMAIN_MIN") -> {
                val parts = line.split(Regex("\\s+"))
                domainMinR = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
                domainMinG = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
                domainMinB = parts.getOrNull(3)?.toFloatOrNull() ?: 0f
            }
            line.startsWith("DOMAIN_MAX") -> {
                val parts = line.split(Regex("\\s+"))
                domainMaxR = parts.getOrNull(1)?.toFloatOrNull() ?: 1f
                domainMaxG = parts.getOrNull(2)?.toFloatOrNull() ?: 1f
                domainMaxB = parts.getOrNull(3)?.toFloatOrNull() ?: 1f
            }
            else -> {
                // Try to parse as "r g b" data row
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 3) {
                    val r = parts[0].toFloatOrNull()
                    val g = parts[1].toFloatOrNull()
                    val b = parts[2].toFloatOrNull()
                    if (r != null && g != null && b != null) {
                        dataLines.add(floatArrayOf(r, g, b))
                    }
                    // If not parseable as floats, skip silently (malformed line)
                }
            }
        }
    }

    if (size <= 0) {
        return CubeLutParseResult.Failure("Missing or invalid LUT_3D_SIZE")
    }

    val expectedRows = size * size * size
    if (dataLines.size != expectedRows) {
        return CubeLutParseResult.Failure("Expected $expectedRows data rows, got ${dataLines.size}")
    }

    // Build float array with domain scaling applied
    val floatData = FloatArray(expectedRows * 3)
    val rangeR = domainMaxR - domainMinR
    val rangeG = domainMaxG - domainMinG
    val rangeB = domainMaxB - domainMinB

    for (i in 0 until expectedRows) {
        val row = dataLines[i]
        val rNorm = if (rangeR != 0f) ((row[0] - domainMinR) / rangeR).coerceIn(0f, 1f) else row[0].coerceIn(0f, 1f)
        val gNorm = if (rangeG != 0f) ((row[1] - domainMinG) / rangeG).coerceIn(0f, 1f) else row[1].coerceIn(0f, 1f)
        val bNorm = if (rangeB != 0f) ((row[2] - domainMinB) / rangeB).coerceIn(0f, 1f) else row[2].coerceIn(0f, 1f)
        floatData[i * 3 + 0] = rNorm
        floatData[i * 3 + 1] = gNorm
        floatData[i * 3 + 2] = bNorm
    }

    return CubeLutParseResult.Success(CubeLut(name = lutName, size = size, data = floatData))
}

fun parseCubeLut(name: String, stream: java.io.InputStream): CubeLutParseResult =
    parseCubeLut(name, stream.bufferedReader(Charsets.UTF_8).readText())
