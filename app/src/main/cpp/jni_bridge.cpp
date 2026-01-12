#include <jni.h>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

#include "engine/Renderer.h"
#include "audio/AudioSystem.h"

#define LOG_TAG "JNI_Bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global instances
static Engine::Renderer* g_renderer = nullptr;
static Audio::AudioSystem* g_audioSystem = nullptr;
static AAssetManager* g_assetManager = nullptr;

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

    // Create and initialize audio system
    if (!g_audioSystem) {
        g_audioSystem = new Audio::AudioSystem();
        if (!g_audioSystem->init(g_assetManager)) {
            LOGE("Failed to initialize audio system");
            // Audio failure is non-fatal, continue without audio
        }
    }

    LOGI("Native engine initialized successfully");
}

JNIEXPORT void JNICALL
Java_com_example_dimohamster_core_NativeRenderer_nativeShutdown(
        JNIEnv* /* env */,
        jobject /* this */) {

    LOGI("Shutting down native engine...");

    if (g_audioSystem) {
        g_audioSystem->shutdown();
        delete g_audioSystem;
        g_audioSystem = nullptr;
    }

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

// =============================================================================
// Audio JNI Functions
// =============================================================================

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

} // extern "C"
