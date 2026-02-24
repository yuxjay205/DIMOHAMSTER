package com.example.dimohamster.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.example.dimohamster.R

/**
 * Singleton manager for playing short sound effects using SoundPool.
 * SoundPool is optimized for low-latency playback of short audio clips.
 */
object SoundEffectManager {
    private const val TAG = "SoundEffectManager"
    private const val MAX_STREAMS = 5

    private var soundPool: SoundPool? = null
    private var shootingSoundId: Int = 0
    private var bounceSoundId: Int = 0
    private var buttonClickSoundId: Int = 0
    private var winSoundId: Int = 0
    private var loseSoundId: Int = 0
    private var isLoaded = false
    private var loadedCount = 0
    private const val TOTAL_SOUNDS = 5
    private var volume = 1.0f

    /**
     * Initialize the SoundPool and load sound effects.
     * Call this once when the app starts.
     */
    fun init(context: Context) {
        if (soundPool != null) {
            return
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                Log.i(TAG, "Sound loaded: $sampleId")
                loadedCount++
                if (loadedCount >= TOTAL_SOUNDS) {
                    isLoaded = true
                    Log.i(TAG, "All sounds loaded")
                }
            } else {
                Log.e(TAG, "Failed to load sound: $sampleId")
            }
        }

        // Load sound effects
        shootingSoundId = soundPool?.load(context, R.raw.shooting, 1) ?: 0
        bounceSoundId = soundPool?.load(context, R.raw.bounce, 1) ?: 0
        buttonClickSoundId = soundPool?.load(context, R.raw.button_click, 1) ?: 0
        winSoundId = soundPool?.load(context, R.raw.win, 1) ?: 0
        loseSoundId = soundPool?.load(context, R.raw.lose, 1) ?: 0
        Log.i(TAG, "SoundEffectManager initialized")
    }

    /**
     * Play the shooting/ball launch sound effect.
     */
    fun playShootingSound() {
        if (isLoaded && shootingSoundId != 0) {
            soundPool?.play(shootingSoundId, volume, volume, 1, 0, 1.0f)
        }
    }

    /**
     * Play the bounce/collision sound effect.
     */
    fun playBounceSound() {
        if (isLoaded && bounceSoundId != 0) {
            soundPool?.play(bounceSoundId, volume, volume, 1, 0, 1.0f)
        }
    }

    /**
     * Play the button click sound effect.
     */
    fun playButtonClickSound() {
        if (isLoaded && buttonClickSoundId != 0) {
            soundPool?.play(buttonClickSoundId, volume, volume, 1, 0, 1.0f)
        }
    }

    /**
     * Play the win/level complete sound effect.
     */
    fun playWinSound() {
        if (isLoaded && winSoundId != 0) {
            soundPool?.play(winSoundId, volume, volume, 1, 0, 1.0f)
        }
    }

    /**
     * Play the lose/game over sound effect.
     */
    fun playLoseSound() {
        if (isLoaded && loseSoundId != 0) {
            soundPool?.play(loseSoundId, volume, volume, 1, 0, 1.0f)
        }
    }

    /**
     * Set the volume for sound effects (0.0 to 1.0).
     */
    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
    }

    /**
     * Get the current volume level.
     */
    fun getVolume(): Float = volume

    /**
     * Release all resources.
     * Call this when the app is completely closing.
     */
    fun release() {
        soundPool?.release()
        soundPool = null
        isLoaded = false
        loadedCount = 0
        shootingSoundId = 0
        bounceSoundId = 0
        buttonClickSoundId = 0
        winSoundId = 0
        loseSoundId = 0
        Log.i(TAG, "SoundEffectManager released")
    }
}
