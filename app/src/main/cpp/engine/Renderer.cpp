#include "Renderer.h"
#include <android/log.h>
#include <glm/gtc/type_ptr.hpp>
#include <chrono>
#include <cstring>

#define LOG_TAG "Renderer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace Engine {

Renderer::Renderer()
    : m_initialized(false)
    , m_width(0)
    , m_height(0)
    , m_assetManager(nullptr)
    , m_testVAO(0)
    , m_testVBO(0)
    , m_testShader(0)
    , m_hasCameraFrame(false)
    , m_cameraFrameWidth(0)
    , m_cameraFrameHeight(0)
    , m_cameraFrameTimestamp(0)
    , m_cameraTexture(0) {
    m_clearColor[0] = 0.1f;
    m_clearColor[1] = 0.1f;
    m_clearColor[2] = 0.15f;
    m_clearColor[3] = 1.0f;

    m_stats = {0, 0, 0.0f, 0.0f};

    m_accelData[0] = m_accelData[1] = m_accelData[2] = 0.0f;
    m_gyroData[0] = m_gyroData[1] = m_gyroData[2] = 0.0f;
}

Renderer::~Renderer() {
    shutdown();
}

bool Renderer::init(AAssetManager* assetManager) {
    m_assetManager = assetManager;
    m_shaderLoader.init(assetManager);

    LOGI("Renderer initialized");
    return true;
}

void Renderer::shutdown() {
    if (m_testVAO) {
        glDeleteVertexArrays(1, &m_testVAO);
        m_testVAO = 0;
    }
    if (m_testVBO) {
        glDeleteBuffers(1, &m_testVBO);
        m_testVBO = 0;
    }
    if (m_cameraTexture) {
        glDeleteTextures(1, &m_cameraTexture);
        m_cameraTexture = 0;
    }

    m_cameraFrameData.clear();
    m_hasCameraFrame = false;

    m_shaderLoader.cleanup();
    m_initialized = false;

    LOGI("Renderer shutdown");
}

void Renderer::onSurfaceCreated() {
    // Log OpenGL ES version info
    const char* version = (const char*)glGetString(GL_VERSION);
    const char* vendor = (const char*)glGetString(GL_VENDOR);
    const char* renderer = (const char*)glGetString(GL_RENDERER);

    LOGI("OpenGL ES Version: %s", version);
    LOGI("Vendor: %s", vendor);
    LOGI("Renderer: %s", renderer);

    // Enable depth testing
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LESS);

    // Enable back-face culling
    glEnable(GL_CULL_FACE);
    glCullFace(GL_BACK);
    glFrontFace(GL_CCW);

    // Enable blending
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    // Setup default shaders and test geometry
    setupDefaultShaders();

    m_initialized = true;
    LOGI("Surface created, OpenGL initialized");
}

void Renderer::onSurfaceChanged(int width, int height) {
    m_width = width;
    m_height = height;

    glViewport(0, 0, width, height);

    // Update camera aspect ratio
    m_camera.resize(static_cast<float>(width), static_cast<float>(height));

    LOGI("Surface changed: %dx%d", width, height);
}

void Renderer::onDrawFrame(float deltaTime) {
    auto frameStart = std::chrono::high_resolution_clock::now();

    // Reset stats
    m_stats.drawCalls = 0;
    m_stats.triangles = 0;

    // Clear buffers
    glClearColor(m_clearColor[0], m_clearColor[1], m_clearColor[2], m_clearColor[3]);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    // Render test triangle
    renderTestTriangle();

    // Calculate frame stats
    auto frameEnd = std::chrono::high_resolution_clock::now();
    m_stats.frameTime = std::chrono::duration<float, std::milli>(frameEnd - frameStart).count();
    m_stats.fps = deltaTime > 0.0f ? 1.0f / deltaTime : 0.0f;
}

void Renderer::setClearColor(float r, float g, float b, float a) {
    m_clearColor[0] = r;
    m_clearColor[1] = g;
    m_clearColor[2] = b;
    m_clearColor[3] = a;
}

void Renderer::updateSensorData(float accelX, float accelY, float accelZ,
                                 float gyroX, float gyroY, float gyroZ) {
    m_accelData[0] = accelX;
    m_accelData[1] = accelY;
    m_accelData[2] = accelZ;

    m_gyroData[0] = gyroX;
    m_gyroData[1] = gyroY;
    m_gyroData[2] = gyroZ;
}

void Renderer::setupDefaultShaders() {
    // Load shaders from assets
    m_testShader = m_shaderLoader.loadProgram("shaders/basic.vert", "shaders/basic.frag");

    if (m_testShader == 0) {
        LOGE("Failed to load default shaders");
        return;
    }

    // Create test triangle
    float vertices[] = {
        // Position (x, y, z)    // Color (r, g, b)
         0.0f,  0.5f, 0.0f,      1.0f, 0.0f, 0.0f,
        -0.5f, -0.5f, 0.0f,      0.0f, 1.0f, 0.0f,
         0.5f, -0.5f, 0.0f,      0.0f, 0.0f, 1.0f
    };

    glGenVertexArrays(1, &m_testVAO);
    glGenBuffers(1, &m_testVBO);

    glBindVertexArray(m_testVAO);

    glBindBuffer(GL_ARRAY_BUFFER, m_testVBO);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);

    // Position attribute
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 6 * sizeof(float), (void*)0);
    glEnableVertexAttribArray(0);

    // Color attribute
    glVertexAttribPointer(1, 3, GL_FLOAT, GL_FALSE, 6 * sizeof(float), (void*)(3 * sizeof(float)));
    glEnableVertexAttribArray(1);

    glBindVertexArray(0);

    LOGI("Default shaders and test geometry created");
}

void Renderer::renderTestTriangle() {
    if (m_testShader == 0 || m_testVAO == 0) {
        return;
    }

    glUseProgram(m_testShader);

    // Set uniforms
    glm::mat4 mvp = m_camera.getViewProjectionMatrix();
    ShaderLoader::setUniform(m_testShader, "u_MVP", glm::value_ptr(mvp));

    // Draw
    glBindVertexArray(m_testVAO);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glBindVertexArray(0);

    m_stats.drawCalls++;
    m_stats.triangles++;
}

void Renderer::updateCameraFrame(int width, int height, const uint8_t* data, size_t dataSize, int64_t timestamp) {
    if (data == nullptr || dataSize == 0) {
        return;
    }

    // Store frame dimensions
    m_cameraFrameWidth = width;
    m_cameraFrameHeight = height;
    m_cameraFrameTimestamp = timestamp;

    // Copy frame data
    if (m_cameraFrameData.size() != dataSize) {
        m_cameraFrameData.resize(dataSize);
    }
    std::memcpy(m_cameraFrameData.data(), data, dataSize);

    m_hasCameraFrame = true;

    // Create texture if not exists
    if (m_cameraTexture == 0) {
        glGenTextures(1, &m_cameraTexture);
        glBindTexture(GL_TEXTURE_2D, m_cameraTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        LOGI("Camera texture created");
    }

    // Convert NV21 to grayscale (Y plane only) for simple display
    // For full color, you'd need a YUV shader or convert to RGB
    glBindTexture(GL_TEXTURE_2D, m_cameraTexture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, width, height, 0,
                 GL_LUMINANCE, GL_UNSIGNED_BYTE, data);
    glBindTexture(GL_TEXTURE_2D, 0);
}

} // namespace Engine
