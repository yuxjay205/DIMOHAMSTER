package com.example.dimohamster.core

/**
 * Listener for game events from native code
 */
object GameEventListener {
    private var onGameOverListener: ((score: Int, level: Int) -> Unit)? = null
    private var onVibrationListener: ((durationMs: Int) -> Unit)? = null
    private var onGoToMainMenuListener: (() -> Unit)? = null

    fun setOnGameOverListener(listener: (score: Int, level: Int) -> Unit) {
        onGameOverListener = listener
    }

    fun setOnVibrationListener(listener: (durationMs: Int) -> Unit) {
        onVibrationListener = listener
    }

    fun setOnGoToMainMenuListener(listener: () -> Unit) {
        onGoToMainMenuListener = listener
    }

    @JvmStatic
    fun onGameOver(score: Int, level: Int) {
        onGameOverListener?.invoke(score, level)
    }

    @JvmStatic
    fun onVibrate(durationMs: Int) {
        onVibrationListener?.invoke(durationMs)
    }

    @JvmStatic
    fun onGoToMainMenu() {
        onGoToMainMenuListener?.invoke()
    }
}
