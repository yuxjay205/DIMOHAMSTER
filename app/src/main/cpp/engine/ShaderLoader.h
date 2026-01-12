#pragma once

#include <GLES3/gl32.h>
#include <android/asset_manager.h>
#include <string>
#include <unordered_map>

namespace Engine {

class ShaderLoader {
public:
    ShaderLoader();
    ~ShaderLoader();

    // Initialize with Android asset manager
    void init(AAssetManager* assetManager);

    // Load and compile shader program from asset files
    GLuint loadProgram(const std::string& vertexPath, const std::string& fragmentPath);

    // Get cached shader program
    GLuint getProgram(const std::string& name) const;

    // Delete all cached shaders
    void cleanup();

    // Uniform helpers
    static void setUniform(GLuint program, const char* name, float value);
    static void setUniform(GLuint program, const char* name, int value);
    static void setUniform(GLuint program, const char* name, const float* matrix4x4);
    static void setUniform(GLuint program, const char* name, float x, float y, float z);
    static void setUniform(GLuint program, const char* name, float x, float y, float z, float w);

private:
    std::string loadShaderSource(const std::string& path);
    GLuint compileShader(GLenum type, const std::string& source);
    GLuint linkProgram(GLuint vertexShader, GLuint fragmentShader);
    void checkCompileErrors(GLuint shader, const std::string& type);
    void checkLinkErrors(GLuint program);

    AAssetManager* m_assetManager;
    std::unordered_map<std::string, GLuint> m_programs;
};

} // namespace Engine
