#include "ShaderLoader.h"
#include <android/log.h>
#include <vector>

#define LOG_TAG "ShaderLoader"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace Engine {

ShaderLoader::ShaderLoader()
    : m_assetManager(nullptr) {
}

ShaderLoader::~ShaderLoader() {
    cleanup();
}

void ShaderLoader::init(AAssetManager* assetManager) {
    m_assetManager = assetManager;
}

std::string ShaderLoader::loadShaderSource(const std::string& path) {
    if (!m_assetManager) {
        LOGE("Asset manager not initialized");
        return "";
    }

    AAsset* asset = AAssetManager_open(m_assetManager, path.c_str(), AASSET_MODE_BUFFER);
    if (!asset) {
        LOGE("Failed to open shader asset: %s", path.c_str());
        return "";
    }

    size_t size = AAsset_getLength(asset);
    std::string source;
    source.resize(size);
    AAsset_read(asset, &source[0], size);
    AAsset_close(asset);

    return source;
}

GLuint ShaderLoader::compileShader(GLenum type, const std::string& source) {
    GLuint shader = glCreateShader(type);
    const char* src = source.c_str();
    glShaderSource(shader, 1, &src, nullptr);
    glCompileShader(shader);

    checkCompileErrors(shader, type == GL_VERTEX_SHADER ? "VERTEX" : "FRAGMENT");

    return shader;
}

GLuint ShaderLoader::linkProgram(GLuint vertexShader, GLuint fragmentShader) {
    GLuint program = glCreateProgram();
    glAttachShader(program, vertexShader);
    glAttachShader(program, fragmentShader);
    glLinkProgram(program);

    checkLinkErrors(program);

    // Shaders can be deleted after linking
    glDeleteShader(vertexShader);
    glDeleteShader(fragmentShader);

    return program;
}

void ShaderLoader::checkCompileErrors(GLuint shader, const std::string& type) {
    GLint success;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &success);
    if (!success) {
        GLint logLength;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &logLength);
        std::vector<char> log(logLength);
        glGetShaderInfoLog(shader, logLength, nullptr, log.data());
        LOGE("%s shader compilation failed:\n%s", type.c_str(), log.data());
    }
}

void ShaderLoader::checkLinkErrors(GLuint program) {
    GLint success;
    glGetProgramiv(program, GL_LINK_STATUS, &success);
    if (!success) {
        GLint logLength;
        glGetProgramiv(program, GL_INFO_LOG_LENGTH, &logLength);
        std::vector<char> log(logLength);
        glGetProgramInfoLog(program, logLength, nullptr, log.data());
        LOGE("Program linking failed:\n%s", log.data());
    }
}

GLuint ShaderLoader::loadProgram(const std::string& vertexPath, const std::string& fragmentPath) {
    // Create a unique name for caching
    std::string programName = vertexPath + "+" + fragmentPath;

    // Check if already cached
    auto it = m_programs.find(programName);
    if (it != m_programs.end()) {
        return it->second;
    }

    // Load shader sources
    std::string vertexSource = loadShaderSource(vertexPath);
    std::string fragmentSource = loadShaderSource(fragmentPath);

    if (vertexSource.empty() || fragmentSource.empty()) {
        LOGE("Failed to load shader sources");
        return 0;
    }

    // Compile shaders
    GLuint vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource);
    GLuint fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource);

    // Link program
    GLuint program = linkProgram(vertexShader, fragmentShader);

    if (program != 0) {
        m_programs[programName] = program;
        LOGI("Shader program loaded: %s", programName.c_str());
    }

    return program;
}

GLuint ShaderLoader::getProgram(const std::string& name) const {
    auto it = m_programs.find(name);
    if (it != m_programs.end()) {
        return it->second;
    }
    return 0;
}

void ShaderLoader::cleanup() {
    for (auto& pair : m_programs) {
        glDeleteProgram(pair.second);
    }
    m_programs.clear();
}

void ShaderLoader::setUniform(GLuint program, const char* name, float value) {
    GLint location = glGetUniformLocation(program, name);
    if (location != -1) {
        glUniform1f(location, value);
    }
}

void ShaderLoader::setUniform(GLuint program, const char* name, int value) {
    GLint location = glGetUniformLocation(program, name);
    if (location != -1) {
        glUniform1i(location, value);
    }
}

void ShaderLoader::setUniform(GLuint program, const char* name, const float* matrix4x4) {
    GLint location = glGetUniformLocation(program, name);
    if (location != -1) {
        glUniformMatrix4fv(location, 1, GL_FALSE, matrix4x4);
    }
}

void ShaderLoader::setUniform(GLuint program, const char* name, float x, float y, float z) {
    GLint location = glGetUniformLocation(program, name);
    if (location != -1) {
        glUniform3f(location, x, y, z);
    }
}

void ShaderLoader::setUniform(GLuint program, const char* name, float x, float y, float z, float w) {
    GLint location = glGetUniformLocation(program, name);
    if (location != -1) {
        glUniform4f(location, x, y, z, w);
    }
}

} // namespace Engine
