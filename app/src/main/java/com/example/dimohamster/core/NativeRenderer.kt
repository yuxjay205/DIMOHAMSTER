package com.example.dimohamster.core

import android.content.res.AssetManager

/**
 * Native renderer bridge for OpenGL ES 3.2 rendering.
 * Wraps the C++ Renderer class via JNI.
 */
object NativeRenderer {

    init {
        System.loadLibrary("dimohamster")
    }

    // Lifecycle
    external fun nativeInit(assetManager: AssetManager)
    external fun nativeShutdown()

    // GLSurfaceView callbacks
    external fun nativeOnSurfaceCreated()
    external fun nativeOnSurfaceChanged(width: Int, height: Int)
    external fun nativeOnDrawFrame()

    // Rendering options
    external fun nativeSetClearColor(r: Float, g: Float, b: Float, a: Float)

    // Camera controls
    external fun nativeSetCameraPosition(x: Float, y: Float, z: Float)
    external fun nativeSetCameraRotation(pitch: Float, yaw: Float)
    external fun nativeLookAt(x: Float, y: Float, z: Float)

    // Convenience methods
    fun setClearColor(r: Float, g: Float, b: Float, a: Float = 1f) {
        nativeSetClearColor(r, g, b, a)
    }

    fun setCameraPosition(x: Float, y: Float, z: Float) {
        nativeSetCameraPosition(x, y, z)
    }

    fun setCameraRotation(pitch: Float, yaw: Float) {
        nativeSetCameraRotation(pitch, yaw)
    }

    fun lookAt(x: Float, y: Float, z: Float) {
        nativeLookAt(x, y, z)
    }
}
