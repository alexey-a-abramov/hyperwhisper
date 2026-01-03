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
        private var libraryLoadAttempted = false
        private var libraryLoadSuccess = false

        init {
            libraryLoadAttempted = true
            try {
                System.loadLibrary("hyperwhisper_jni")
                libraryLoadSuccess = true
                Log.d(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                libraryLoadSuccess = false
                Log.e(TAG, "Failed to load native library: ${e.message}", e)
                Log.e(TAG, "This usually means:")
                Log.e(TAG, "  1. Native libraries are not included in this build variant")
                Log.e(TAG, "  2. Using cloud/cloudOnly build variant but trying to use LOCAL mode")
                Log.e(TAG, "  3. Native libraries for this architecture are missing")
            } catch (e: Throwable) {
                libraryLoadSuccess = false
                Log.e(TAG, "Unexpected error loading native library", e)
            }
        }

        /**
         * Check if the native library was successfully loaded
         */
        fun isLibraryAvailable(): Boolean = libraryLoadSuccess
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
        if (!libraryLoadSuccess) {
            return Result.failure(Exception(
                "Native library not available. LOCAL mode requires the 'local' build variant with native libraries. " +
                "Please use a cloud API provider or install the local build variant."
            ))
        }

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
        } catch (e: Throwable) {
            Log.e(TAG, "Error loading model", e)
            Result.failure(Exception("Failed to load model: ${e.message}"))
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
        if (!libraryLoadSuccess) {
            return Result.failure(Exception(
                "Native library not available. LOCAL mode requires the 'local' build variant with native libraries."
            ))
        }

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
        } catch (e: Throwable) {
            Log.e(TAG, "Error transcribing audio", e)
            Result.failure(Exception("Transcription failed: ${e.message}"))
        }
    }

    /**
     * Unload the currently loaded model to free memory
     */
    fun unloadModel() {
        if (!libraryLoadSuccess) return

        try {
            if (nativeIsModelLoaded()) {
                Log.d(TAG, "Unloading model")
                nativeUnloadModel()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error unloading model", e)
        }
    }

    /**
     * Check if a model is currently loaded
     * @return True if a model is loaded, false otherwise
     */
    fun isModelLoaded(): Boolean {
        if (!libraryLoadSuccess) return false

        return try {
            nativeIsModelLoaded()
        } catch (e: Throwable) {
            Log.e(TAG, "Error checking if model is loaded", e)
            false
        }
    }
}
