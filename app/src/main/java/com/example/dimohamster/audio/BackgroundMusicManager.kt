package com.example.dimohamster.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.example.dimohamster.R

/**
 * Singleton manager for background music playback across all activities.
 * Music continues playing when switching between activities.
 */
object BackgroundMusicManager {
    private const val TAG = "BackgroundMusicManager"

    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private var currentVolume = 0.5f  // Default 50% volume
    private var isMuted = false
    private var volumeBeforeMute = 0.5f

    /**
     * Initialize and start playing background music.
     * Call this from your launcher activity.
     */
    fun start(context: Context) {
        if (mediaPlayer != null && isPrepared) {
            // Already playing, just resume if paused
            if (!mediaPlayer!!.isPlaying) {
                mediaPlayer?.start()
                Log.i(TAG, "Resumed background music")
            }
            return
        }

        try {
            release()  // Clean up any existing player

            mediaPlayer = MediaPlayer.create(context.applicationContext, R.raw.bgm)
            mediaPlayer?.apply {
                isLooping = true
                setVolume(currentVolume, currentVolume)
                start()
                isPrepared = true
                Log.i(TAG, "Started background music")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting background music: ${e.message}")
        }
    }

    /**
     * Pause the background music.
     * Call this when the app goes to background.
     */
    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                Log.i(TAG, "Paused background music")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing background music: ${e.message}")
        }
    }

    /**
     * Resume the background music.
     * Call this when the app returns to foreground.
     */
    fun resume() {
        try {
            if (isPrepared && mediaPlayer?.isPlaying == false) {
                mediaPlayer?.start()
                Log.i(TAG, "Resumed background music")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming background music: ${e.message}")
        }
    }

    /**
     * Set the volume level (0.0 to 1.0).
     */
    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        try {
            mediaPlayer?.setVolume(currentVolume, currentVolume)
            Log.i(TAG, "Volume set to: $currentVolume")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume: ${e.message}")
        }
    }

    /**
     * Get the current volume level.
     */
    fun getVolume(): Float = currentVolume

    /**
     * Mute the background music (saves current volume for unmuting).
     */
    fun mute() {
        if (!isMuted) {
            volumeBeforeMute = currentVolume
            isMuted = true
            try {
                mediaPlayer?.setVolume(0f, 0f)
                Log.i(TAG, "Background music muted")
            } catch (e: Exception) {
                Log.e(TAG, "Error muting background music: ${e.message}")
            }
        }
    }

    /**
     * Unmute the background music (restores previous volume).
     */
    fun unmute() {
        if (isMuted) {
            isMuted = false
            try {
                mediaPlayer?.setVolume(volumeBeforeMute, volumeBeforeMute)
                currentVolume = volumeBeforeMute
                Log.i(TAG, "Background music unmuted to volume: $currentVolume")
            } catch (e: Exception) {
                Log.e(TAG, "Error unmuting background music: ${e.message}")
            }
        }
    }

    /**
     * Check if music is currently muted.
     */
    fun isMuted(): Boolean = isMuted

    /**
     * Check if music is currently playing.
     */
    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    /**
     * Release all resources.
     * Call this when the app is completely closing.
     */
    fun release() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            isPrepared = false
            Log.i(TAG, "Released background music resources")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing background music: ${e.message}")
        }
    }
}
