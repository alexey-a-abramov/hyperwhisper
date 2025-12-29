package com.hyperwhisper.native_whisper

import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kotlin wrapper for whisper.cpp JNI interface
 * Provides safe access to native whisper transcription functionality
 */
@Singleton
class WhisperContext @Inject constructor() {

    companion object {
        private const val TAG = "WhisperContext"

        init {
            try {
                System.loadLibrary("hyperwhisper_jni")
                Log.d(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
            }
        }
    }

    // JNI methods
    private external fun nativeLoadModel(modelPath: String): Boolean
    private external fun nativeTranscribe(
        audioPath: String,
        language: String,
        translate: Boolean
    ): String
    private external fun nativeUnloadModel()
    private external fun nativeIsModelLoaded(): Boolean

    /**
     * Load a whisper model from file
     * @param modelFile The model file to load
     * @return Result indicating success or failure
     */
    fun loadModel(modelFile: File): Result<Unit> {
        return try {
            if (!modelFile.exists()) {
                return Result.failure(Exception("Model file not found: ${modelFile.absolutePath}"))
            }

            Log.d(TAG, "Loading model: ${modelFile.absolutePath} (${modelFile.length()} bytes)")
            val success = nativeLoadModel(modelFile.absolutePath)

            if (success) {
                Log.d(TAG, "Model loaded successfully")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to load model"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            Result.failure(e)
        }
    }

    /**
     * Transcribe audio file using the loaded model
     * @param audioFile WAV audio file (16kHz, mono, 16-bit PCM)
     * @param language Language code (ISO-639-1) or empty for auto-detect
     * @param translate Whether to translate to English
     * @return Result containing transcription text or error
     */
    fun transcribe(
        audioFile: File,
        language: String = "",
        translate: Boolean = false
    ): Result<String> {
        return try {
            if (!nativeIsModelLoaded()) {
                return Result.failure(Exception("Model not loaded"))
            }

            if (!audioFile.exists()) {
                return Result.failure(Exception("Audio file not found: ${audioFile.absolutePath}"))
            }

            Log.d(TAG, "Transcribing: ${audioFile.name} (${audioFile.length()} bytes), lang=$language, translate=$translate")
            val result = nativeTranscribe(audioFile.absolutePath, language, translate)

            if (result.isNotEmpty()) {
                Log.d(TAG, "Transcription successful: ${result.length} chars")
                Result.success(result)
            } else {
                Result.failure(Exception("Transcription returned empty result"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error transcribing audio", e)
            Result.failure(e)
        }
    }

    /**
     * Unload the currently loaded model to free memory
     */
    fun unloadModel() {
        try {
            if (nativeIsModelLoaded()) {
                Log.d(TAG, "Unloading model")
                nativeUnloadModel()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading model", e)
        }
    }

    /**
     * Check if a model is currently loaded
     * @return True if a model is loaded, false otherwise
     */
    fun isModelLoaded(): Boolean {
        return try {
            nativeIsModelLoaded()
        } catch (e: Exception) {
            false
        }
    }
}
