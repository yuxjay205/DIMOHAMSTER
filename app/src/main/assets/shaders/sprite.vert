#version 310 es

// Vertex attributes
layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_TexCoord;
layout(location = 2) in vec4 a_Color;

// Uniforms
uniform mat4 u_MVP;
uniform mat4 u_Model;

// Output to fragment shader
out vec2 v_TexCoord;
out vec4 v_Color;

void main() {
    gl_Position = u_MVP * u_Model * vec4(a_Position, 1.0);
    v_TexCoord = a_TexCoord;
    v_Color = a_Color;
}
