#pragma once

#include <GLES3/gl32.h>
#include <EGL/egl.h>
#include <android/asset_manager.h>
#include <memory>

#include "Camera.h"
#include "ShaderLoader.h"

namespace Engine {

struct RenderStats {
    int drawCalls;
    int triangles;
    float frameTime;
    float fps;
};

class Renderer {
public:
    Renderer();
    ~Renderer();

    // Lifecycle
    bool init(AAssetManager* assetManager);
    void shutdown();

    // Frame rendering
    void onSurfaceCreated();
    void onSurfaceChanged(int width, int height);
    void onDrawFrame(float deltaTime);

    // Background color
    void setClearColor(float r, float g, float b, float a = 1.0f);

    // Camera access
    Camera& getCamera() { return m_camera; }
    const Camera& getCamera() const { return m_camera; }

    // Shader access
    ShaderLoader& getShaderLoader() { return m_shaderLoader; }

    // Stats
    const RenderStats& getStats() const { return m_stats; }

    // Screen dimensions
    int getWidth() const { return m_width; }
    int getHeight() const { return m_height; }

    // Sensor input (for tilt controls)
    void updateSensorData(float accelX, float accelY, float accelZ,
                          float gyroX, float gyroY, float gyroZ);

private:
    void setupDefaultShaders();
    void renderTestTriangle();

    // OpenGL state
    bool m_initialized;
    int m_width;
    int m_height;

    // Clear color
    float m_clearColor[4];

    // Core systems
    Camera m_camera;
    ShaderLoader m_shaderLoader;
    AAssetManager* m_assetManager;

    // Test geometry
    GLuint m_testVAO;
    GLuint m_testVBO;
    GLuint m_testShader;

    // Stats
    RenderStats m_stats;

    // Sensor data
    float m_accelData[3];
    float m_gyroData[3];
};

} // namespace Engine
