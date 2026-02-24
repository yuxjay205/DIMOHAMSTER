package com.example.dimohamster.core

/**
 * Listener for game events from native code
 */
object GameEventListener {
    private var onGameOverListener: ((score: Int, level: Int) -> Unit)? = null
    private var onVibrationListener: ((durationMs: Int) -> Unit)? = null
    private var onGoToMainMenuListener: (() -> Unit)? = null
    private var onBounceSoundListener: (() -> Unit)? = null
    private var onLevelCompleteListener: (() -> Unit)? = null
    private var onGameOverSoundListener: (() -> Unit)? = null
    private var onGameResumeListener: (() -> Unit)? = null

    fun setOnGameOverListener(listener: (score: Int, level: Int) -> Unit) {
        onGameOverListener = listener
    }

    fun setOnVibrationListener(listener: (durationMs: Int) -> Unit) {
        onVibrationListener = listener
    }

    fun setOnGoToMainMenuListener(listener: () -> Unit) {
        onGoToMainMenuListener = listener
    }

    fun setOnBounceSoundListener(listener: () -> Unit) {
        onBounceSoundListener = listener
    }

    fun setOnLevelCompleteListener(listener: () -> Unit) {
        onLevelCompleteListener = listener
    }

    fun setOnGameOverSoundListener(listener: () -> Unit) {
        onGameOverSoundListener = listener
    }

    fun setOnGameResumeListener(listener: () -> Unit) {
        onGameResumeListener = listener
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

    @JvmStatic
    fun onBounceSound() {
        onBounceSoundListener?.invoke()
    }

    @JvmStatic
    fun onLevelComplete() {
        onLevelCompleteListener?.invoke()
    }

    @JvmStatic
    fun onGameOverSound() {
        onGameOverSoundListener?.invoke()
    }

    @JvmStatic
    fun onGameResume() {
        onGameResumeListener?.invoke()
    }
}
