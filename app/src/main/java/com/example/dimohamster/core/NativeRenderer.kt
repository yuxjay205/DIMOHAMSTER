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

    // Device camera frame processing
    external fun nativeUpdateCameraFrame(width: Int, height: Int, data: ByteArray, timestamp: Long)
    external fun nativeOnNoseDetected(normX: Float, normY: Float)
    external fun nativeSetCameraFrameEnabled(enabled: Boolean)
    external fun nativeIsCameraFrameEnabled(): Boolean

    // Touch input for game
    external fun nativeTouchDown(x: Float, y: Float)
    external fun nativeTouchMove(x: Float, y: Float)
    external fun nativeTouchUp(x: Float, y: Float)

    // Sensor data (for simulation support)
    external fun nativeUpdateSensorData(sensorType: Int, x: Float, y: Float, z: Float)

    // Game settings
    external fun nativeSetNoseSmoothingFactor(factor: Float)
    external fun nativeSetSensitivity(sensitivity: Float)
    external fun nativeSetTrajectoryPreviewEnabled(enabled: Boolean)
    external fun nativeSetShowCameraBackground(show: Boolean)
    external fun nativeSetPaused(paused: Boolean)

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

    /**
     * Update device camera frame data for AR/video processing
     * @param width Frame width in pixels
     * @param height Frame height in pixels
     * @param data NV21 format frame data
     * @param timestamp Frame timestamp in nanoseconds
     */
    fun updateCameraFrame(width: Int, height: Int, data: ByteArray, timestamp: Long) {
        nativeUpdateCameraFrame(width, height, data, timestamp)
    }

    fun onNoseDetected(normX: Float, normY: Float) {
        nativeOnNoseDetected(normX, normY)
    }

    /**
     * Enable or disable camera frame processing
     */
    fun setCameraFrameEnabled(enabled: Boolean) {
        nativeSetCameraFrameEnabled(enabled)
    }

    /**
     * Check if camera frame processing is enabled
     */
    fun isCameraFrameEnabled(): Boolean {
        return nativeIsCameraFrameEnabled()
    }

    /**
     * Update sensor data from simulation
     * @param sensorType 0 = accelerometer, 1 = gyroscope
     */
    fun updateSensorData(sensorType: Int, x: Float, y: Float, z: Float) {
        nativeUpdateSensorData(sensorType, x, y, z)
    }

    /**
     * Set nose smoothing factor (0.0-1.0)
     * Lower values = more smoothing, higher values = more responsive
     */
    fun setNoseSmoothingFactor(factor: Float) {
        nativeSetNoseSmoothingFactor(factor)
    }

    /**
     * Set nose control sensitivity (0.5-1.5)
     */
    fun setSensitivity(sensitivity: Float) {
        nativeSetSensitivity(sensitivity)
    }

    /**
     * Enable or disable trajectory preview
     */
    fun setTrajectoryPreviewEnabled(enabled: Boolean) {
        nativeSetTrajectoryPreviewEnabled(enabled)
    }

    /**
     * Show camera background or solid color
     */
    fun setShowCameraBackground(show: Boolean) {
        nativeSetShowCameraBackground(show)
    }

    /**
     * Pause or resume the game
     */
    fun setPaused(paused: Boolean) {
        nativeSetPaused(paused)
    }
}
