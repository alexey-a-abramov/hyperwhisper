package com.hyperwhisper.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages sound effects for the application
 * Provides audio feedback for various events like recording timeout
 */
@Singleton
class SoundManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "SoundManager"
        private const val BEEP_DURATION_MS = 200
    }

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ToneGenerator", e)
        }
    }

    /**
     * Play a notification beep to indicate recording was cut due to max duration
     */
    fun playRecordingCutSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS)
            Log.d(TAG, "Playing recording cut notification sound")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play recording cut sound", e)
        }
    }

    /**
     * Play a success beep
     */
    fun playSuccessSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, BEEP_DURATION_MS)
            Log.d(TAG, "Playing success sound")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play success sound", e)
        }
    }

    /**
     * Play an error beep
     */
    fun playErrorSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, BEEP_DURATION_MS)
            Log.d(TAG, "Playing error sound")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play error sound", e)
        }
    }

    /**
     * Release resources
     */
    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ToneGenerator", e)
        }
    }
}
