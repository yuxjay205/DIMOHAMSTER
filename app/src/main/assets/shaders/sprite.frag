#version 310 es

precision highp float;

// Input from vertex shader
in vec2 v_TexCoord;
in vec4 v_Color;

// Uniforms
uniform sampler2D u_Texture;
uniform float u_Alpha;

// Output color
out vec4 FragColor;

void main() {
    vec4 texColor = texture(u_Texture, v_TexCoord);
    FragColor = texColor * v_Color * vec4(1.0, 1.0, 1.0, u_Alpha);

    // Discard fully transparent pixels
    if (FragColor.a < 0.01) {
        discard;
    }
}
