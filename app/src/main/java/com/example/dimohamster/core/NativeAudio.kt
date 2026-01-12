package com.example.dimohamster.core

/**
 * Native audio bridge for FMOD Studio integration.
 * Wraps the C++ AudioSystem class via JNI.
 */
object NativeAudio {

    init {
        System.loadLibrary("dimohamster")
    }

    // Bank management
    external fun nativeLoadBank(bankPath: String): Boolean
    external fun nativeUnloadBank(bankPath: String)

    // Event playback
    external fun nativePlayEvent(eventPath: String): Boolean
    external fun nativePlayEventAtPosition(eventPath: String, x: Float, y: Float, z: Float): Boolean
    external fun nativeStopEvent(eventPath: String, immediate: Boolean)

    // Global controls
    external fun nativeSetMasterVolume(volume: Float)
    external fun nativePauseAll(paused: Boolean)

    // Convenience methods
    fun loadBank(bankPath: String): Boolean = nativeLoadBank(bankPath)

    fun unloadBank(bankPath: String) = nativeUnloadBank(bankPath)

    fun playEvent(eventPath: String): Boolean = nativePlayEvent(eventPath)

    fun playEventAtPosition(eventPath: String, x: Float, y: Float, z: Float): Boolean =
        nativePlayEventAtPosition(eventPath, x, y, z)

    fun stopEvent(eventPath: String, immediate: Boolean = false) =
        nativeStopEvent(eventPath, immediate)

    fun setMasterVolume(volume: Float) = nativeSetMasterVolume(volume.coerceIn(0f, 1f))

    fun pauseAll(paused: Boolean) = nativePauseAll(paused)
}
