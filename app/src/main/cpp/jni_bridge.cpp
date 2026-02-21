#include <jni.h>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <vector>
#include <mutex>

#include "engine/Renderer.h"

#if FMOD_ENABLED
#include "audio/AudioSystem.h"
#endif

#define LOG_TAG "JNI_Bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global instances
static Engine::Renderer* g_renderer = nullptr;
static AAssetManager* g_assetManager = nullptr;

#if FMOD_ENABLED
static Audio::AudioSystem* g_audioSystem = nullptr;
#endif

// Frame timing
static auto g_lastFrameTime = std::chrono::high_resolution_clock::now();

extern "C" {

// =============================================================================
// Renderer JNI Functions
// =============================================================================

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeRenderer_nativeInit(
        JNIEnv* env,
        jobject /* this */,
        jobject assetManager) {

    LOGI("Initializing native engine...");

    // Get native asset manager
    g_assetManager = AAssetManager_fromJava(env, assetManager);
    if (!g_assetManager) {
        LOGE("Failed to get asset manager");
        return;
    }

    // Create and initialize renderer
    if (!g_renderer) {
        g_renderer = new Engine::Renderer();
        if (!g_renderer->init(g_assetManager)) {
            LOGE("Failed to initialize renderer");
            delete g_renderer;
            g_renderer = nullptr;
            return;
        }
    }

    // Create and initialize audio system (if FMOD is available)
#if FMOD_ENABLED
    if (!g_audioSystem) {
        g_audioSystem = new Audio::AudioSystem();
        if (!g_audioSystem->init(g_assetManager)) {
            LOGE("Failed to initialize audio system");
            // Audio failure is non-fatal, continue without audio
        }
    }
#else
    LOGI("Audio system disabled (FMOD not available)");
#endif

    LOGI("Native engine initialized successfully");
}

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeRenderer_nativeShutdown(
        JNIEnv* /* env */,
        jobject /* this */) {

    LOGI("Shutting down native engine...");

#if FMOD_ENABLED
    if (g_audioSystem) {
        g_audioSystem->shutdown();
        delete g_audioSystem;
        g_audioSystem = nullptr;
    }
#endif

    if (g_renderer) {
        g_renderer->shutdown();
        delete g_renderer;
        g_renderer = nullptr;
    }

    g_assetManager = nullptr;

    LOGI("Native engine shut down");
}

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeRenderer_nativeOnSurfaceCreated(
        JNIEnv* /* env */,
        jobject /* this */) {

    if (g_renderer) {
        g_renderer->onSurfaceCreated();
    }
}

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeRenderer_nativeOnSurfaceChanged(
        JNIEnv* /* env */,
        jobject /* this */,
        jint width,
        jint height) {

    if (g_renderer) {
        g_renderer->onSurfaceChanged(width, height);
    }
}

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeRenderer_nativeOnDrawFrame(
        JNIEnv* /* env */,
        jobject /* this */) {

    // Calculate delta time
    auto currentTime = std::chrono::high_resolution_clock::now();
    float deltaTime = std::chrono::duration<float>(currentTime - g_lastFrameTime).count();
    g_lastFrameTime = currentTime;

    // Update and render
    if (g_renderer) {
        g_renderer->onDrawFrame(deltaTime);
    }

#if FMOD_ENABLED
    // Update audio system
    if (g_audioSystem) {
        g_audioSystem->update();

        // Sync audio listener with camera
        if (g_renderer) {
            glm::vec3 pos, vel, fwd, up;
            g_renderer->getCamera().getListenerAttributes(pos, vel, fwd, up);
            Audio::ListenerAttributes attrs = {pos, vel, fwd, up};
            g_audioSystem->setListenerAttributes(attrs);
        }
    }
#endif
}

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeRenderer_nativeSetClearColor(
        JNIEnv* /* env */,
        jobject /* this */,
        jfloat r,
        jfloat g,
        jfloat b,
        jfloat a) {

    if (g_renderer) {
        g_renderer->setClearColor(r, g, b, a);
    }
}

// =============================================================================
// Touch Input JNI Functions
// =============================================================================

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeRenderer_nativeTouchDown(
        JNIEnv* /* env */,
        jobject /* this */,
        jfloat x,
        jfloat y) {

    if (g_renderer) {
        g_renderer->onTouchDown(x, y);
    }
}

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeRenderer_nativeTouchMove(
        JNIEnv* /* env */,
        jobject /* this */,
        jfloat x,
        jfloat y) {

    if (g_renderer) {
        g_renderer->onTouchMove(x, y);
    }
}

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeRenderer_nativeTouchUp(
        JNIEnv* /* env */,
        jobject /* this */,
        jfloat x,
        jfloat y) {

    if (g_renderer) {
        g_renderer->onTouchUp(x, y);
    }
}

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeRenderer_nativeOnNoseDetected(
        JNIEnv* /* env */,
        jobject /* this */,
        jfloat normX,
        jfloat normY) {
    if (g_renderer) {
        g_renderer->onNoseDetected(normX, normY);
    }
}

// =============================================================================
// Camera JNI Functions
// =============================================================================

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeRenderer_nativeSetCameraPosition(
        JNIEnv* /* env */,
        jobject /* this */,
        jfloat x,
        jfloat y,
        jfloat z) {

    if (g_renderer) {
        g_renderer->getCamera().setPosition(glm::vec3(x, y, z));
    }
}

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeRenderer_nativeSetCameraRotation(
        JNIEnv* /* env */,
        jobject /* this */,
        jfloat pitch,
        jfloat yaw) {

    if (g_renderer) {
        g_renderer->getCamera().setRotation(pitch, yaw);
    }
}

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeRenderer_nativeLookAt(
        JNIEnv* /* env */,
        jobject /* this */,
        jfloat x,
        jfloat y,
        jfloat z) {

    if (g_renderer) {
        g_renderer->getCamera().lookAt(glm::vec3(x, y, z));
    }
}

// =============================================================================
// Sensor Data JNI Functions
// =============================================================================

JNIEXPORT void JNICALL
Java_com_example_dimohamster_sensors_SensorBridge_nativeUpdateSensorData(
        JNIEnv* /* env */,
        jobject /* this */,
        jfloat accelX,
        jfloat accelY,
        jfloat accelZ,
        jfloat gyroX,
        jfloat gyroY,
        jfloat gyroZ) {

    if (g_renderer) {
        g_renderer->updateSensorData(accelX, accelY, accelZ, gyroX, gyroY, gyroZ);
    }
}

// Sensor data update from NativeRenderer (for simulation support)
JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeRenderer_nativeUpdateSensorData(
        JNIEnv* /* env */,
        jobject /* this */,
        jint sensorType,
        jfloat x,
        jfloat y,
        jfloat z) {

    if (g_renderer) {
        if (sensorType == 0) {
            // Accelerometer - update with zero gyro
            g_renderer->updateSensorData(x, y, z, 0, 0, 0);
        } else if (sensorType == 1) {
            // Gyroscope - update with zero accel (preserving gravity)
            g_renderer->updateSensorData(0, 0, 9.81f, x, y, z);
        }
    }
}

// =============================================================================
// Camera Frame JNI Functions
// =============================================================================

// Camera frame state
static bool g_cameraFrameEnabled = false;
static int g_cameraFrameWidth = 0;
static int g_cameraFrameHeight = 0;
static std::vector<uint8_t> g_cameraFrameData;
static int64_t g_cameraFrameTimestamp = 0;
static std::mutex g_cameraFrameMutex;

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeRenderer_nativeUpdateCameraFrame(
        JNIEnv* env,
        jobject /* this */,
        jint width,
        jint height,
        jbyteArray data,
        jlong timestamp) {

    if (!g_cameraFrameEnabled) {
        return;
    }

    jsize dataLength = env->GetArrayLength(data);
    if (dataLength <= 0) {
        return;
    }

    std::lock_guard<std::mutex> lock(g_cameraFrameMutex);

    // Resize buffer if needed
    if (g_cameraFrameData.size() != static_cast<size_t>(dataLength)) {
        g_cameraFrameData.resize(dataLength);
    }

    // Copy frame data
    env->GetByteArrayRegion(data, 0, dataLength,
                            reinterpret_cast<jbyte*>(g_cameraFrameData.data()));

    g_cameraFrameWidth = width;
    g_cameraFrameHeight = height;
    g_cameraFrameTimestamp = timestamp;

    // Pass to renderer for processing/display
    if (g_renderer) {
        g_renderer->updateCameraFrame(width, height, g_cameraFrameData.data(), dataLength, timestamp);
    }
}

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeRenderer_nativeSetCameraFrameEnabled(
        JNIEnv* /* env */,
        jobject /* this */,
        jboolean enabled) {

    g_cameraFrameEnabled = (enabled == JNI_TRUE);
    LOGI("Camera frame processing %s", g_cameraFrameEnabled ? "enabled" : "disabled");

    if (!g_cameraFrameEnabled) {
        // Clear buffer when disabled
        std::lock_guard<std::mutex> lock(g_cameraFrameMutex);
        g_cameraFrameData.clear();
        g_cameraFrameWidth = 0;
        g_cameraFrameHeight = 0;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_example_dimohamster_core_NativeRenderer_nativeIsCameraFrameEnabled(
        JNIEnv* /* env */,
        jobject /* this */) {

    return g_cameraFrameEnabled ? JNI_TRUE : JNI_FALSE;
}

// =============================================================================
// Audio JNI Functions (only if FMOD is available)
// =============================================================================

#if FMOD_ENABLED

JNIEXPORT jboolean JNICALL
Java_com_example_dimohamster_core_NativeAudio_nativeLoadBank(
        JNIEnv* env,
        jobject /* this */,
        jstring bankPath) {

    if (!g_audioSystem) {
        return JNI_FALSE;
    }

    const char* path = env->GetStringUTFChars(bankPath, nullptr);
    bool result = g_audioSystem->loadBank(path);
    env->ReleaseStringUTFChars(bankPath, path);

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeAudio_nativeUnloadBank(
        JNIEnv* env,
        jobject /* this */,
        jstring bankPath) {

    if (!g_audioSystem) {
        return;
    }

    const char* path = env->GetStringUTFChars(bankPath, nullptr);
    g_audioSystem->unloadBank(path);
    env->ReleaseStringUTFChars(bankPath, path);
}

JNIEXPORT jboolean JNICALL
Java_com_example_dimohamster_core_NativeAudio_nativePlayEvent(
        JNIEnv* env,
        jobject /* this */,
        jstring eventPath) {

    if (!g_audioSystem) {
        return JNI_FALSE;
    }

    const char* path = env->GetStringUTFChars(eventPath, nullptr);
    bool result = g_audioSystem->playEvent(path);
    env->ReleaseStringUTFChars(eventPath, path);

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_dimohamster_core_NativeAudio_nativePlayEventAtPosition(
        JNIEnv* env,
        jobject /* this */,
        jstring eventPath,
        jfloat x,
        jfloat y,
        jfloat z) {

    if (!g_audioSystem) {
        return JNI_FALSE;
    }

    const char* path = env->GetStringUTFChars(eventPath, nullptr);
    bool result = g_audioSystem->playEventAtPosition(path, glm::vec3(x, y, z));
    env->ReleaseStringUTFChars(eventPath, path);

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeAudio_nativeStopEvent(
        JNIEnv* env,
        jobject /* this */,
        jstring eventPath,
        jboolean immediate) {

    if (!g_audioSystem) {
        return;
    }

    const char* path = env->GetStringUTFChars(eventPath, nullptr);
    g_audioSystem->stopEvent(path, immediate == JNI_TRUE);
    env->ReleaseStringUTFChars(eventPath, path);
}

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeAudio_nativeSetMasterVolume(
        JNIEnv* /* env */,
        jobject /* this */,
        jfloat volume) {

    if (g_audioSystem) {
        g_audioSystem->setMasterVolume(volume);
    }
}

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeAudio_nativePauseAll(
        JNIEnv* /* env */,
        jobject /* this */,
        jboolean paused) {

    if (g_audioSystem) {
        g_audioSystem->pauseAll(paused == JNI_TRUE);
    }
}

#endif // FMOD_ENABLED

} // extern "C"
