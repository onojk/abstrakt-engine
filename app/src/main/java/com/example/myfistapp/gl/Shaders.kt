package com.example.myfistapp.gl

internal object Shaders {

    // Shared passthrough vertex shader — identical for all full-screen quad programs.
    val TEST_VERT = """
        #version 300 es
        layout(location = 0) in vec2 a_position;
        out vec2 v_uv;
        void main() {
            v_uv = a_position * 0.5 + 0.5;
            gl_Position = vec4(a_position, 0.0, 1.0);
        }
    """.trimIndent()

    val TEST_FRAG = """
        #version 300 es
        precision mediump float;
        uniform float u_time;
        uniform vec2  u_resolution;
        uniform float u_peak;
        uniform float u_beat;
        uniform float u_bands[8];
        in vec2 v_uv;
        out vec4 fragColor;
        void main() {
            float wave = 0.5 + 0.5 * sin(u_time * (2.0 + u_peak * 8.0) + v_uv.x * 6.28);
            vec3 color = vec3(v_uv.x, v_uv.y, wave);
            color = mix(color, vec3(1.0), u_beat * 0.4);
            color *= 0.4 + u_peak * 0.6;
            fragColor = vec4(color, 1.0);
        }
    """.trimIndent()

    val WARP_VERT    = TEST_VERT
    val KALEIDO_VERT = TEST_VERT

    val WARP_FRAG = """
        #version 300 es
        precision mediump float;

        const int INF_COUNT = 5;

        uniform float u_time;
        uniform vec2  u_resolution;
        uniform float u_peak;
        uniform float u_beat;
        uniform float u_bands[8];
        uniform float u_grid_dim;
        uniform float u_dot_radius;
        uniform vec2  u_influencer_pos[5];
        uniform float u_influencer_strength[5];
        uniform float u_inf_radius;

        in  vec2 v_uv;
        out vec4 fragColor;

        vec3 hsv2rgb(vec3 c) {
            vec4 K = vec4(1.0, 2.0/3.0, 1.0/3.0, 3.0);
            vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
            return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
        }

        vec2 computeOffset(vec2 homePos) {
            vec2 total = vec2(0.0);
            float r2   = u_inf_radius * u_inf_radius;
            for (int k = 0; k < INF_COUNT; k++) {
                vec2  diff = homePos - u_influencer_pos[k];
                float d2   = dot(diff, diff);
                if (d2 < r2 && d2 > 0.25) {
                    float d    = sqrt(d2);
                    float fall = 1.0 - d / u_inf_radius;
                    float force = u_influencer_strength[k] * fall * fall;
                    float sgn   = (mod(float(k), 2.0) < 1.0) ? 1.0 : -1.0;
                    total += sgn * (diff / d) * force;
                }
            }
            return total;
        }

        void main() {
            vec2  fragCoord = v_uv * u_resolution;
            float minDim    = min(u_resolution.x, u_resolution.y);
            float cellSize  = minDim / u_grid_dim;

            float hueBase = u_bands[0] * 200.0;
            float sat     = clamp(0.65 + u_bands[2] * 0.35, 0.0, 1.0);
            float bval    = clamp(0.45 + u_peak * 0.45 + u_beat * 0.10, 0.0, 1.0);

            float minDist    = 1.0e6;
            float closestMag = 0.0;

            vec2 cellBase = floor(fragCoord / cellSize);

            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    vec2  cell    = cellBase + vec2(float(dx), float(dy));
                    vec2  homePos = (cell + 0.5) * cellSize;
                    vec2  offset  = computeOffset(homePos);
                    float d       = length(fragCoord - (homePos + offset));
                    if (d < minDist) {
                        minDist    = d;
                        closestMag = length(offset);
                    }
                }
            }

            // smoothstep with edge0 < edge1 — well-defined per GLSL ES spec.
            // At dot center (d=0): alpha=1. At outer radius (d=u_dot_radius): alpha=0.
            float alpha = 1.0 - smoothstep(u_dot_radius * 0.4, u_dot_radius, minDist);

            // Hue: base from low band + magnitude of displacement + slow time drift.
            float hue = mod(hueBase + closestMag * 0.35 + u_time * 2.0, 360.0) / 360.0;
            vec3  rgb = hsv2rgb(vec3(hue, sat, bval));

            // Premultiply alpha into RGB; output fully opaque pixel (black between dots).
            fragColor = vec4(rgb * alpha, 1.0);
        }
    """.trimIndent()

    val DRIFT_FRAG = """
        #version 300 es
        precision mediump float;

        const int   NUM_BARS   = 32;
        const int   NUM_BANDS  = 8;
        const int   NUM_LAYERS = 4;
        const int   HIST_FRAMES = 6;

        // Layer read offsets into u_band_ring (1 slot ≈ 80 ms).
        const int   LAG[4]          = int[4](0, 1, 3, 6);
        const float LAYER_ALPHA[4]  = float[4](1.0, 0.75, 0.55, 0.40);
        const float HUE_SHIFT[4]    = float[4](0.00, 0.12, 0.25, 0.38);

        // 12 ticks × 8 bands: slot 0 = current, slot 11 = 11 ticks ago.
        uniform float u_band_ring[96];
        // 4 scrambles × 32 cols: u_scramble[layer*32 + col] = band index (0..7).
        uniform float u_scramble[128];
        uniform float u_time;

        in  vec2 v_uv;
        out vec4 fragColor;

        vec3 hsv2rgb(vec3 c) {
            vec4 K = vec4(1.0, 2.0/3.0, 1.0/3.0, 3.0);
            vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
            return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
        }

        void main() {
            float col_f    = v_uv.x * float(NUM_BARS);
            int   col      = clamp(int(col_f), 0, NUM_BARS - 1);
            float edgeFrac = fract(col_f);
            float edgeAA   = smoothstep(0.0, 0.03, edgeFrac) * smoothstep(1.0, 0.97, edgeFrac);

            vec3 result = vec3(0.0);

            for (int l = 0; l < NUM_LAYERS; l++) {
                int   bandIdx = int(u_scramble[l * NUM_BARS + col]);
                float baseHue = float(bandIdx) / float(NUM_BANDS)
                              + HUE_SHIFT[l] + u_time * 0.02;

                // Ghost frames oldest→newest; newer ghosts paint over older.
                for (int age = HIST_FRAMES - 1; age >= 0; age--) {
                    int   tick = LAG[l] + age;
                    float h    = u_band_ring[tick * NUM_BANDS + bandIdx];
                    if ((1.0 - v_uv.y) <= h) {
                        float t          = 1.0 - float(age) / float(HIST_FRAMES);
                        float ghostAlpha = t * t * 0.55 * LAYER_ALPHA[l];
                        float val        = (0.35 + h * 0.65) * 0.6;
                        result = mix(result, hsv2rgb(vec3(baseHue, 0.85, val)), ghostAlpha);
                    }
                }
            }

            fragColor = vec4(result * edgeAA, 1.0);
        }
    """.trimIndent()

    val KALEIDO_FRAG = """
        #version 300 es
        precision mediump float;

        const float PI = 3.14159265358979;

        uniform sampler2D u_source;
        uniform vec2      u_resolution;
        uniform float     u_time;
        uniform int       u_segments;
        uniform float     u_rotation;

        in  vec2 v_uv;
        out vec4 fragColor;

        void main() {
            vec2  p        = v_uv - vec2(0.5);
            float r        = length(p);
            float angle    = atan(p.y, p.x) + u_rotation * u_time;

            float segAngle = PI / float(u_segments);
            angle = mod(angle, segAngle * 2.0);
            if (angle > segAngle) angle = segAngle * 2.0 - angle;

            vec2 sampleUV = clamp(
                vec2(r * cos(angle), r * sin(angle)) + vec2(0.5),
                0.0, 1.0
            );
            fragColor = texture(u_source, sampleUV);
        }
    """.trimIndent()

    val DRIFT_POLAR_FRAG = """
        #version 300 es
        precision mediump float;

        // NOTE: Bars are computed in polar space directly, not rendered cartesian
        // and folded. The earlier two-pass attempt (vertical bars to FBO, then
        // radial kaleido sample) failed because vertical fills are geometrically
        // incompatible with radial sampling. Future visualizers with meaningful
        // x/y structure may still want the FBO+kaleido pipeline.

        const float PI          = 3.14159265358979;
        const int   NUM_BARS    = 32;
        const int   NUM_BANDS   = 8;
        const int   NUM_LAYERS  = 4;
        const int   HIST_FRAMES = 6;
        const int   SEGMENTS    = 12;

        const int   LAG[4]         = int[4](0, 1, 3, 6);
        const float LAYER_ALPHA[4] = float[4](1.0, 0.75, 0.55, 0.40);
        const float HUE_SHIFT[4]   = float[4](0.00, 0.12, 0.25, 0.38);

        // 12 ticks x 8 bands: slot 0 = current, slot 11 = 11 ticks ago.
        uniform float u_band_ring[96];
        // 4 scrambles x 32 cols: u_scramble[layer*32 + col] = band index (0..7).
        uniform float u_scramble[128];
        uniform float u_time;
        // CPU-side beat decay: spikes to 1.0 on beat, decays to 0 over ~200ms.
        uniform float u_beat_decay;

        in  vec2 v_uv;
        out vec4 fragColor;

        vec3 hsv2rgb(vec3 c) {
            vec4 K = vec4(1.0, 2.0/3.0, 1.0/3.0, 3.0);
            vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
            return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
        }

        void main() {
            vec2  p     = v_uv - vec2(0.5);
            float r     = length(p) * 2.0;         // 0 = center, 1 = inscribed-circle edge
            float angle = atan(p.y, p.x);          // -PI..PI

            // 12-fold kaleidoscope fold: unique wedge is [0, PI/12].
            float segAngle = PI / float(SEGMENTS);  // 15 degrees
            angle = mod(angle, 2.0 * segAngle);     // [0, 30 deg]
            if (angle > segAngle) angle = 2.0 * segAngle - angle;  // mirror to [0, 15 deg]

            // Map folded angle to column index (0..NUM_BARS-1).
            float col_f    = (angle / segAngle) * float(NUM_BARS);
            int   col      = clamp(int(col_f), 0, NUM_BARS - 1);
            float edgeFrac = fract(col_f);
            float edgeAA   = smoothstep(0.0, 0.04, edgeFrac)
                           * smoothstep(1.0, 0.96, edgeFrac);

            // ── Anchor layer (rendered first — solid foundation) ──────────────
            // Uses layer-0 scramble with current bands (tick 0, no lag).
            int   anchorBand = int(u_scramble[col]);   // layer 0 scramble
            float anchorH    = u_band_ring[anchorBand]; // current (tick = 0)
            vec3  result     = vec3(0.0);
            if (r <= anchorH) {
                // Deep navy base, flashes electric blue on beat.
                vec3 anchorBase = vec3(0.04, 0.06, 0.55);
                result = anchorBase * (1.0 + 0.4 * u_beat_decay);
            }

            // ── Ghost layers (oldest → newest, composite over anchor) ─────────
            for (int l = 0; l < NUM_LAYERS; l++) {
                int   bandIdx = int(u_scramble[l * NUM_BARS + col]);
                float baseHue = float(bandIdx) / float(NUM_BANDS)
                              + HUE_SHIFT[l] + u_time * 0.02;

                // Ghost frames oldest to newest so newer frames paint over older.
                for (int age = HIST_FRAMES - 1; age >= 0; age--) {
                    int   tick = LAG[l] + age;
                    float h    = u_band_ring[tick * NUM_BANDS + bandIdx];
                    if (r <= h) {
                        float t          = 1.0 - float(age) / float(HIST_FRAMES);
                        float ghostAlpha = t * t * 0.55 * LAYER_ALPHA[l];
                        float val        = (0.35 + h * 0.65) * 0.6;
                        result = mix(result, hsv2rgb(vec3(baseHue, 0.85, val)), ghostAlpha);
                    }
                }
            }

            // Soft dark-hole at center, circular fade at inscribed-circle edge.
            float fade = smoothstep(0.0, 0.08, r) * (1.0 - smoothstep(0.85, 1.0, r));

            fragColor = vec4(result * edgeAA * fade, 1.0);
        }
    """.trimIndent()
}
