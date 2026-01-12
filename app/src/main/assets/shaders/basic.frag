#version 320 es

precision highp float;

// Input from vertex shader
in vec3 v_Color;

// Output color
out vec4 FragColor;

void main() {
    FragColor = vec4(v_Color, 1.0);
}
