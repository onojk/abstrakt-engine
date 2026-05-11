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
        precision highp float;
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
        precision highp float;

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
        precision highp float;

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
        precision highp float;

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

    val CYCLONE_VERT = """
        #version 300 es
        layout(location = 0) in vec3 a_position;
        layout(location = 1) in vec2 a_uv;
        uniform mat4 u_mvp;
        out vec2 v_uv;
        void main() {
            v_uv = a_uv;
            gl_Position = u_mvp * vec4(a_position, 1.0);
        }
    """.trimIndent()

    // Samples the rolling painter texture — no procedural logic here.
    val CYCLONE_FRAG = """
        #version 300 es
        precision mediump float;
        uniform sampler2D u_painterTexture;
        uniform int   u_invert_colors;
        uniform int   u_colorize_enabled;
        uniform float u_colorize_hue;          // degrees 0..360
        uniform int   u_distortion_enabled;
        uniform float u_distortion_amplitude;  // 0..1
        uniform float u_distortion_frequency;  // 0.5..8.0
        uniform float u_contrast;              // 0..2, 1=passthrough
        uniform int   u_contrast_passes;       // 1..6, >1=posterization
        uniform float u_saturation;            // 0..2, 1=passthrough
        uniform float u_time;
        in  vec2 v_uv;
        out vec4 fragColor;

        vec3 hsv2rgb(float h, float s, float v) {
            float c  = v * s;
            float h6 = h / 60.0;
            float x  = c * (1.0 - abs(mod(h6, 2.0) - 1.0));
            vec3 rgb;
            if      (h6 < 1.0) rgb = vec3(c, x, 0.0);
            else if (h6 < 2.0) rgb = vec3(x, c, 0.0);
            else if (h6 < 3.0) rgb = vec3(0.0, c, x);
            else if (h6 < 4.0) rgb = vec3(0.0, x, c);
            else if (h6 < 5.0) rgb = vec3(x, 0.0, c);
            else               rgb = vec3(c, 0.0, x);
            return rgb + vec3(v - c);
        }

        void main() {
            vec2 uv = v_uv;
            if (u_distortion_enabled == 1) {
                float freq = u_distortion_frequency;
                float waveU = sin(uv.y * freq * 6.28318 + u_time * freq * 1.5);
                float waveV = sin(uv.x * freq * 6.28318 + u_time * freq * 1.2);
                uv.x += waveU * u_distortion_amplitude * 0.05;
                uv.y += waveV * u_distortion_amplitude * 0.05;
            }
            vec4 c = texture(u_painterTexture, uv);
            for (int i = 0; i < 6; ++i) {
                if (i >= u_contrast_passes) break;
                c.rgb = clamp((c.rgb - vec3(0.5)) * u_contrast + vec3(0.5), 0.0, 1.0);
            }
            float luma = dot(c.rgb, vec3(0.299, 0.587, 0.114));
            c.rgb = clamp(mix(vec3(luma), c.rgb, u_saturation), 0.0, 1.0);
            if (u_invert_colors == 1)    c.rgb  = vec3(1.0) - c.rgb;
            if (u_colorize_enabled == 1) c.rgb *= hsv2rgb(u_colorize_hue, 1.0, 1.0);
            fragColor = c;
        }
    """.trimIndent()

    // Painter uses the same fullscreen-quad vertex shader as the 2D modes.
    val PAINTER_VERT = TEST_VERT

    // Writes a solid hue band (varying with time) + vertical brightness gradient.
    // Hue cycles every 20s so each stripe's age is visually readable.
    val PAINTER_HUESTRIPE_FRAG = """
        #version 300 es
        precision highp float;
        uniform float u_time;
        in  vec2 v_uv;
        out vec4 fragColor;

        vec3 hsv2rgb(vec3 c) {
            vec4 K = vec4(1.0, 2.0/3.0, 1.0/3.0, 3.0);
            vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
            return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
        }

        void main() {
            float hue = fract(u_time * 0.05);          // full cycle every 20s
            float val = 0.4 + 0.6 * v_uv.y;           // darker bottom, brighter top
            fragColor = vec4(hsv2rgb(vec3(hue, 1.0, val)), 1.0);
        }
    """.trimIndent()

    // Audio-reactive painter: 8 frequency bands mapped to 8 vertical color columns.
    // Each column's hue, brightness, and saturation track its band's energy.
    val PAINTER_AUDIOPAINT_FRAG = """
        #version 300 es
        precision highp float;
        uniform float u_time;
        uniform float u_peak;
        uniform float u_beat_decay;
        uniform float u_bands[8];
        in  vec2 v_uv;
        out vec4 fragColor;

        vec3 hsv2rgb(vec3 c) {
            vec4 K = vec4(1.0, 2.0/3.0, 1.0/3.0, 3.0);
            vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
            return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
        }

        void main() {
            int   bandIdx = clamp(int(v_uv.x * 8.0), 0, 7);
            float amp     = u_bands[bandIdx];
            float hue     = float(bandIdx) / 8.0 + u_time * 0.008;
            float sat     = 0.80 + amp * 0.20;
            float val     = (0.25 + amp * 0.65) * (1.0 + u_beat_decay * 0.6)
                          * (0.35 + 0.65 * v_uv.y);
            fragColor = vec4(hsv2rgb(vec3(fract(hue), sat, clamp(val, 0.0, 1.0))), 1.0);
        }
    """.trimIndent()

    // CYCLONE_KALEIDO_FRAG — folds the rendered shape FBO into a kaleidoscope.
    // u_content is the shape FBO (screen-resolution 3D render), not the painter strip.
    // Rotation of the shape is already baked into the FBO; no UV-scroll math needed.
    val CYCLONE_KALEIDO_FRAG = """
        #version 300 es
        precision highp float;

        const float PI = 3.14159265;

        uniform sampler2D u_content;
        uniform vec2      u_resolution;
        uniform float     u_kaleido_rotation;
        uniform float     u_kaleido_folds;
        uniform float     u_kaleido_rotation_offset;
        uniform float     u_kaleido_zoom;

        in  vec2 v_uv;
        out vec4 fragColor;

        void main() {
            float minDim   = min(u_resolution.x, u_resolution.y);
            vec2  centered = (v_uv * u_resolution - u_resolution * 0.5) / minDim;

            float r     = length(centered);
            float theta = atan(centered.y, centered.x) + u_kaleido_rotation + u_kaleido_rotation_offset;

            // Fold into one kaleido sector.
            float segAngle = 2.0 * PI / u_kaleido_folds;
            theta = mod(theta, segAngle);
            if (theta > segAngle * 0.5) theta = segAngle - theta;

            // Reconstruct folded point in the same normalised screen space,
            // then map back to UV [0,1] to sample the shape FBO.
            // Inverse of: centered = (v_uv * res - res*0.5) / minDim
            //         =>  v_uv = centered * minDim / res + 0.5
            vec2 folded   = vec2(cos(theta) * r, sin(theta) * r);
            vec2 sampleUV = folded * minDim / u_resolution * u_kaleido_zoom + 0.5;

            fragColor = texture(u_content, sampleUV);
        }
    """.trimIndent()

    // FRAME_OVERLAY_FRAG — SDF shape mask applied to kaleido FBO output.
    // Shapes (u_frame_shape): 0=None, 1=Circle, 2=Square, 3=Rounded, 4=Hexagon, 5=Octagon, 6=Flower, 7=Star
    // Coordinates normalised to shorter screen dimension (same basis as kaleido shader).
    val FRAME_OVERLAY_FRAG = """
        #version 300 es
        precision highp float;

        in  vec2 v_uv;
        out vec4 fragColor;

        uniform sampler2D u_kaleido_tex;
        uniform vec2      u_resolution;
        uniform int       u_frame_shape;
        uniform vec4      u_frame_color;
        uniform float     u_frame_size;

        vec2 centeredCoord(vec2 uv, vec2 res) {
            float minDim = min(res.x, res.y);
            return (uv * res - res * 0.5) / minDim;
        }

        float sdfCircle(vec2 p, float r) {
            return length(p) - r;
        }

        float sdfBox(vec2 p, float h) {
            vec2 d = abs(p) - vec2(h);
            return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
        }

        float sdfRoundedBox(vec2 p, float h, float corner) {
            return sdfBox(p, h - corner) - corner;
        }

        float sdfHexagon(vec2 p, float r) {
            vec3 k = vec3(-0.866025404, 0.5, 0.577350269);
            p = abs(p);
            p -= 2.0 * min(dot(k.xy, p), 0.0) * k.xy;
            p -= vec2(clamp(p.x, -k.z * r, k.z * r), r);
            return length(p) * sign(p.y);
        }

        float sdfOctagon(vec2 p, float h) {
            vec3 k = vec3(-0.9238795325, 0.3826834323, 0.4142135623);
            p = abs(p);
            p -= 2.0 * min(dot(vec2( k.x, k.y), p), 0.0) * vec2( k.x, k.y);
            p -= 2.0 * min(dot(vec2(-k.x, k.y), p), 0.0) * vec2(-k.x, k.y);
            p -= vec2(clamp(p.x, -k.z * h, k.z * h), h);
            return length(p) * sign(p.y);
        }

        float sdfFlower(vec2 p, float numPetals, float baseR, float petalAmp) {
            float angle  = atan(p.y, p.x);
            float radius = length(p);
            float petalR = baseR + petalAmp * cos(numPetals * angle);
            return radius - petalR;
        }

        float sdfStar5(vec2 p, float r, float rf) {
            vec2 k1 = vec2(0.809016994, -0.587785252);
            vec2 k2 = vec2(-k1.x, k1.y);
            p.x = abs(p.x);
            p -= 2.0 * max(dot(k1, p), 0.0) * k1;
            p -= 2.0 * max(dot(k2, p), 0.0) * k2;
            p.x = abs(p.x);
            p.y -= r;
            vec2 ba = rf * vec2(-k1.y, k1.x) - vec2(0.0, 1.0);
            float h = clamp(dot(p, ba) / dot(ba, ba), 0.0, r);
            return length(p - ba * h) * sign(p.y * ba.x - p.x * ba.y);
        }

        void main() {
            vec4 kaleido = texture(u_kaleido_tex, v_uv);

            if (u_frame_shape == 0) {
                fragColor = kaleido;
                return;
            }

            vec2  p   = centeredCoord(v_uv, u_resolution);
            float h   = u_frame_size;
            float sdf = 1.0;

            if      (u_frame_shape == 1) sdf = sdfCircle(p, h);
            else if (u_frame_shape == 2) sdf = sdfBox(p, h);
            else if (u_frame_shape == 3) sdf = sdfRoundedBox(p, h, h * 0.2);
            else if (u_frame_shape == 4) sdf = sdfHexagon(p, h);
            else if (u_frame_shape == 5) sdf = sdfOctagon(p, h);
            else if (u_frame_shape == 6) sdf = sdfFlower(p, 5.0, h * 0.7, h * 0.3);
            else if (u_frame_shape == 7) sdf = sdfStar5(p, h, 0.5);

            float pw   = 1.5 / min(u_resolution.x, u_resolution.y);
            float mask = 1.0 - smoothstep(-pw, pw, sdf);

            vec3 tinted  = mix(kaleido.rgb, u_frame_color.rgb, u_frame_color.a);
            vec3 finalRgb = mix(tinted, kaleido.rgb, mask);
            fragColor = vec4(finalRgb, 1.0);
        }
    """.trimIndent()

    // PrintHead painter v2: transparent gaps + audio-reactive colored dots.
    // Non-dot pixels output alpha=0 so the painter FBO retains previous content in gaps.
    // Requires GL_BLEND enabled during the painter pass (handled in AbstraktRenderer Pass 1).
    // Jobs cycle every 5s; each job picks a random center (hash11) and a base hue.
    // Dot brightness scales with u_peak; hue shifts per job. FBO aspect 4:1.
    val PAINTER_PRINTHEAD_FRAG = """
        #version 300 es
        precision highp float;

        uniform float u_time;
        uniform float u_peak;
        uniform float u_bands[8];

        in  vec2 v_uv;
        out vec4 fragColor;

        const float JOB_DURATION  = 5.0;
        const float CIRCLE_RADIUS = 0.18;
        const float GRID_SIZE     = 24.0;
        const float DOT_SIZE      = 4.0;

        float hash11(float n) {
            return fract(sin(n * 12.9898 + 78.233) * 43758.5453);
        }

        vec3 hsv2rgb(vec3 c) {
            vec4 K = vec4(1.0, 2.0/3.0, 1.0/3.0, 3.0);
            vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
            return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
        }

        void main() {
            vec2 fboSize = vec2(4096.0, 256.0);
            vec2 px      = v_uv * fboSize;

            float jobIndex = floor(u_time / JOB_DURATION);

            float cx = hash11(jobIndex * 1.0);
            float cy = 0.3 + hash11(jobIndex * 1.7) * 0.4;
            vec2 center = vec2(cx, cy);

            vec2 gridPx = floor(px / GRID_SIZE) * GRID_SIZE + GRID_SIZE * 0.5;
            vec2 gridUV = gridPx / fboSize;

            // Aspect-correct distance: vec2(4,1) makes the test circular in pixel space.
            vec2  aspectDelta    = (gridUV - center) * vec2(fboSize.x / fboSize.y, 1.0);
            float distFromCenter = length(aspectDelta);

            if (distFromCenter >= CIRCLE_RADIUS) {
                fragColor = vec4(0.0);   // transparent — leave FBO content intact
                return;
            }

            float distToCell = length(px - gridPx);
            float dotMask    = 1.0 - smoothstep(DOT_SIZE - 0.5, DOT_SIZE + 0.5, distToCell);

            if (dotMask < 0.01) {
                fragColor = vec4(0.0);   // gap between dots inside circle — also transparent
                return;
            }

            // Audio-reactive color: job-seeded hue, peak-driven brightness.
            float baseHue   = hash11(jobIndex * 2.3);
            float audioBoost = 0.6 + u_peak * 0.4;
            vec3 col = hsv2rgb(vec3(baseHue, 0.85, audioBoost));

            fragColor = vec4(col, dotMask);
        }
    """.trimIndent()

    // Image painter: samples a bundled JPG asset. Horizontal position is driven by
    // u_cyclone_angle so the image window scrolls in sync with cylinder rotation.
    // Opaque (alpha 1.0) — overwrites FBO like HueStripe/AudioPaint.
    // For a 4096-wide source image and 1024-wide FBO: 4 full revolutions per image cycle.
    val PAINTER_IMAGE_FRAG = """
        #version 300 es
        precision mediump float;

        uniform sampler2D u_image;
        uniform float     u_image_width;    // source image width in pixels (e.g. 4096)
        uniform float     u_cyclone_angle;  // rotation accumulator in radians

        in  vec2 v_uv;
        out vec4 fragColor;

        const float PI = 3.14159265;

        void main() {
            // One revolution advances the image window by (FBO_width / image_width).
            // For 4096-wide image: window advances 0.25 per revolution → 4 revs per cycle.
            float rotationProgress  = u_cyclone_angle / (2.0 * PI);
            float fboFractionOfImage = 4096.0 / u_image_width;
            float sourceU = rotationProgress + v_uv.x * fboFractionOfImage;

            // GL_REPEAT wraps sourceU for values outside [0,1].
            fragColor = vec4(texture(u_image, vec2(sourceU, v_uv.y)).rgb, 1.0);
        }
    """.trimIndent()

    val DRIFT_POLAR_FRAG = """
        #version 300 es
        precision highp float;

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

    val BLIT_FRAG = """
        #version 300 es
        precision mediump float;
        uniform sampler2D u_tex;
        in  vec2 v_uv;
        out vec4 fragColor;
        void main() { fragColor = texture(u_tex, v_uv); }
    """.trimIndent()

    // Runs in painter-texture UV space (1024×256).
    // v_uv.x = angle 0..1 (= 0..2π around the mandala).
    // v_uv.y = radial 0..1 (kaleido samples this as r = v / 1.9).
    // Ribbons are horizontal sinusoidal waves at target V positions;
    // the kaleido 12-fold fold provides all angular replication.
    val RIBBON_FRAG = """
        #version 300 es
        precision highp float;

        const float TRAIL_DECAY = 0.992;
        const float TWO_PI      = 6.28318530718;

        uniform vec2      u_resolution;
        uniform float     u_time;
        uniform float     u_bands[8];
        uniform vec4      u_collapse;
        uniform vec3      u_ribbon_color;
        uniform sampler2D u_trail;

        in  vec2 v_uv;
        out vec4 fragColor;

        void main() {
            // Polar-unwrap space: x=angle, y=radius
            float theta = v_uv.x * TWO_PI;
            float v     = v_uv.y;

            float bass    = (u_bands[0] + u_bands[1]) * 0.5;
            float mid     = (u_bands[2] + u_bands[3] + u_bands[4]) / 3.0;
            float treble  = (u_bands[5] + u_bands[6] + u_bands[7]) / 3.0;
            float overall = (bass + mid + treble) / 3.0;

            // base* = target V in painter texture = target_kaleido_r * 1.9
            // kaleido radii: 0.16, 0.26, 0.38, 0.47  →  V: 0.30, 0.50, 0.72, 0.90
            float base0 = mix(0.30, 0.0, u_collapse[0]);
            float base1 = mix(0.50, 0.0, u_collapse[1]);
            float base2 = mix(0.72, 0.0, u_collapse[2]);
            float base3 = mix(0.90, 0.0, u_collapse[3]);

            // Harmonic variation — no cos(12θ), kaleido fold provides symmetry
            float curveV0 = base0 * (1.0 + 0.08*cos(2.0*theta + u_time*0.2) + bass*0.10);
            float curveV1 = base1 * (1.0 + 0.10*cos(4.0*theta - u_time*0.3) + mid*0.10);
            float curveV2 = base2 * (1.0 + 0.06*cos(6.0*theta + u_time*0.4) + treble*0.08);
            float curveV3 = base3 * (1.0 + 0.05*cos(2.0*theta - u_time*0.15) + overall*0.06);

            float maxCollapse = max(max(u_collapse[0], u_collapse[1]), max(u_collapse[2], u_collapse[3]));
            float coreHalfPx  = mix(1.0, 2.5, maxCollapse);
            float coreHalf    = coreHalfPx / u_resolution.y;

            float line0 = 1.0 - smoothstep(0.0, coreHalf, abs(v - curveV0));
            float line1 = 1.0 - smoothstep(0.0, coreHalf, abs(v - curveV1));
            float line2 = 1.0 - smoothstep(0.0, coreHalf, abs(v - curveV2));
            float line3 = 1.0 - smoothstep(0.0, coreHalf, abs(v - curveV3));

            float lineIntensity = clamp(line0 + line1 + line2 + line3, 0.0, 1.0);

            vec4  prev         = texture(u_trail, v_uv);
            float decayedAlpha = prev.a * TRAIL_DECAY;
            float outA         = min(max(decayedAlpha, lineIntensity * 1.3), 1.0);

            fragColor = vec4(u_ribbon_color, outA);
        }
    """.trimIndent()

    // Spiral painter: 5-arm color spiral, aspect-corrected for the 4:1 strip.
    // Arms are hue-mapped and rotate slowly over time.
    val PAINTER_SPIRAL_FRAG = """
        #version 300 es
        precision highp float;

        const float TAU = 6.28318530717959;

        uniform float u_time;

        in  vec2 v_uv;
        out vec4 fragColor;

        vec3 hsv2rgb(vec3 c) {
            vec4 K = vec4(1.0, 2.0/3.0, 1.0/3.0, 3.0);
            vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
            return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
        }

        void main() {
            const float aspect    = 4.0;   // artistic — strip is 16:1 but 4:1 gives denser arms
            const float arm_count = 5.0;

            vec2  p     = v_uv * 2.0 - 1.0;
            p.x        *= aspect;

            float r     = length(p);
            float theta = atan(p.y, p.x);

            float hue        = fract(theta / TAU * arm_count + r * 1.5 - u_time * 0.1);
            float brightness = clamp(0.7 + 0.3 * (1.0 - r * 0.4), 0.0, 1.0);

            fragColor = vec4(hsv2rgb(vec3(hue, 1.0, brightness)), 1.0);
        }
    """.trimIndent()

    // Plasma painter: demoscene-style flowing color blobs from 4 additive sine waves.
    val PAINTER_PLASMA_FRAG = """
        #version 300 es
        precision highp float;

        uniform float u_time;

        in  vec2 v_uv;
        out vec4 fragColor;

        vec3 hsv2rgb(vec3 c) {
            vec4 K = vec4(1.0, 2.0/3.0, 1.0/3.0, 3.0);
            vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
            return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
        }

        void main() {
            vec2 uv = v_uv * 4.0 - 2.0;   // remap to [-2, 2] on both axes

            float v = 0.0;
            v += sin(uv.x * 1.5 + u_time * 0.7);
            v += sin(uv.y * 1.5 + u_time * 0.9);
            v += sin((uv.x + uv.y) * 1.0 + u_time * 0.5);
            v += sin(length(uv) * 2.0 - u_time * 0.6);
            v *= 0.25;   // normalise to approx [-1, 1]

            float hue = fract(v * 0.5 + 0.5 + u_time * 0.03);

            fragColor = vec4(hsv2rgb(vec3(hue, 0.9, 0.95)), 1.0);
        }
    """.trimIndent()

    // Skin painter: directly samples the skin texture 1:1 (4096×256 skin → 4096×256 FBO).
    // GL_REPEAT on S is set on the skin texture at load time so edges tile cleanly.
    val PAINTER_SKIN_FRAG = """
        #version 300 es
        precision mediump float;
        uniform sampler2D u_skin;
        in  vec2 v_uv;
        out vec4 fragColor;
        void main() {
            fragColor = vec4(texture(u_skin, v_uv).rgb, 1.0);
        }
    """.trimIndent()

    // Pass 2.5 — equirectangular yaw/pitch/roll rotation of the shape FBO.
    // Standard equirectangular panorama math; reimplemented from first principles.
    // When all angles are 0 the output is pixel-identical to the input.
    // Wrap mode: GL_REPEAT on S (yaw wraps the horizon), GL_CLAMP_TO_EDGE on T (poles).
    val DISTORTION_PLUS_FRAG = """
        #version 300 es
        precision highp float;

        in  vec2 v_uv;
        out vec4 fragColor;

        uniform sampler2D u_content;
        uniform float u_yaw;    // radians
        uniform float u_pitch;  // radians
        uniform float u_roll;   // radians

        const float PI  = 3.14159265359;
        const float TAU = 6.28318530718;

        void main() {
            // 1. Output UV -> spherical (lon, lat)
            float lon = (v_uv.x - 0.5) * TAU;
            float lat = (v_uv.y - 0.5) * PI;

            // 2. Spherical -> Cartesian unit vector
            vec3 p = vec3(
                cos(lat) * sin(lon),
                sin(lat),
                cos(lat) * cos(lon)
            );

            // 3. Apply roll (Z), pitch (X), yaw (Y) in that order.
            float cr = cos(u_roll),  sr = sin(u_roll);
            float cp = cos(u_pitch), sp = sin(u_pitch);
            float cy = cos(u_yaw),   sy = sin(u_yaw);

            // Roll around Z
            p = vec3(cr*p.x - sr*p.y, sr*p.x + cr*p.y, p.z);
            // Pitch around X
            p = vec3(p.x, cp*p.y - sp*p.z, sp*p.y + cp*p.z);
            // Yaw around Y
            p = vec3(cy*p.x + sy*p.z, p.y, -sy*p.x + cy*p.z);

            // 4. Cartesian -> spherical
            float lonOut = atan(p.x, p.z);
            float latOut = asin(clamp(p.y, -1.0, 1.0));

            // 5. Spherical -> source UV
            vec2 src = vec2(lonOut / TAU + 0.5, latOut / PI + 0.5);

            fragColor = texture(u_content, src);
        }
    """.trimIndent()
}
