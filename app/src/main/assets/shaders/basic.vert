#version 320 es

// Vertex attributes
layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec3 a_Color;

// Uniforms
uniform mat4 u_MVP;

// Output to fragment shader
out vec3 v_Color;

void main() {
    gl_Position = u_MVP * vec4(a_Position, 1.0);
    v_Color = a_Color;
}
