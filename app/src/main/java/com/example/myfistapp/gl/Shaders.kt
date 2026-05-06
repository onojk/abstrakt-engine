package com.example.myfistapp.gl

internal object Shaders {

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
}
