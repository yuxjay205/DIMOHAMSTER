package com.example.dimohamster.core

/**
 * Listener for game events from native code
 */
object GameEventListener {
    private var onGameOverListener: ((score: Int, level: Int) -> Unit)? = null

    fun setOnGameOverListener(listener: (score: Int, level: Int) -> Unit) {
        onGameOverListener = listener
    }

    @JvmStatic
    fun onGameOver(score: Int, level: Int) {
        onGameOverListener?.invoke(score, level)
    }
}
