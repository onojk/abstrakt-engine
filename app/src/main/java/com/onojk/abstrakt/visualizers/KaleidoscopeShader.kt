package com.onojk.abstrakt.visualizers

// NOTE: This is no longer aiming to match the desktop frei0r kaleidoscope.
// The "Echo" visualizer is evolving as its own thing under user direction.
// Internal class names retain "Kaleidoscope" for code stability; user-facing
// label is "Echo". Do not rename the class.
//
// AGSL (Android Graphics Shading Language) kaleidoscope post-effect.
// Matches the visual character of the frei0r kaleid0sc0pe filter used in the
// desktop abstrakt pipeline (full_pipeline.sh): 12-wedge radial mirror,
// center at (0.5, 0.5), optional slow rotation.
//
// Sampling notes:
//   - content.eval() takes pixel coordinates [0, resolution].
//   - For pixels where r > 0.5 (outside the inscribed circle — screen corners),
//     the folded sample coord can exceed [0, resolution]. Clamped to
//     [0, resolution - 1] to avoid wrap-around. Corner pixels will sample
//     from the nearest edge of the warpfield rather than from garbage memory.
//     If this produces visible edge streaks, report to the user before fixing.

internal val KALEIDOSCOPE_AGSL = """
    const float PI = 3.14159265;

    uniform shader content;
    uniform int    segments;
    uniform float  rotation;
    uniform float2 resolution;

    half4 main(float2 fragCoord) {
        // Use a single scale (minDim) so the polar fold is circular, not elliptical.
        // Without this, portrait screens produce ovals instead of radial wedges.
        float minDim  = min(resolution.x, resolution.y);
        float2 centered = (fragCoord - resolution * 0.5) / minDim;

        float r     = length(centered);
        float theta = atan(centered.y, centered.x) + rotation;

        // Fold theta into one mirror-symmetric wedge of width segAngle
        float segAngle = 2.0 * PI / float(segments);
        theta = mod(theta, segAngle);
        if (theta > segAngle * 0.5) {
            theta = segAngle - theta;
        }

        // Back to pixel coords using the same minDim scale — clamp to [0, res-1]
        float2 samplePos = clamp(
            float2(cos(theta), sin(theta)) * r * minDim + resolution * 0.5,
            float2(0.0),
            resolution - 1.0
        );
        return content.eval(samplePos);
    }
""".trimIndent()
