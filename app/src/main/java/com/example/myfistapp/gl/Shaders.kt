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

    val WARP_VERT = TEST_VERT   // same passthrough vertex shader

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
}
