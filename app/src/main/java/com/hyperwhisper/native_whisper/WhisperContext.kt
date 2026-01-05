package com.hyperwhisper.native_whisper

import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Progress callback for real-time transcription progress updates
 */
interface WhisperProgressCallback {
    /**
     * Called when transcription progress updates
     * @param progress Progress value from 0 to 100
     */
    fun onProgress(progress: Int)
}

/**
 * Segment callback for real-time text streaming during transcription
 */
interface WhisperSegmentCallback {
    /**
     * Called when a new text segment is transcribed
     * @param text The transcribed text segment
     * @param startTime Start time in milliseconds
     * @param endTime End time in milliseconds
     */
    fun onSegment(text: String, startTime: Long, endTime: Long)
}

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
    private external fun nativeSetProgressCallback(callback: Any?)
    private external fun nativeSetSegmentCallback(callback: Any?)
    private external fun nativeClearCallbacks()

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

            Log.d(TAG, "[TIMING] Native transcription starting...")
            Log.d(TAG, "Transcribing: ${audioFile.name} (${audioFile.length()} bytes), lang=$language, translate=$translate")

            val startTime = System.currentTimeMillis()
            val result = nativeTranscribe(audioFile.absolutePath, language, translate)
            val elapsedMs = System.currentTimeMillis() - startTime

            Log.d(TAG, "[TIMING] Native transcription completed in ${elapsedMs}ms (${String.format("%.2f", elapsedMs / 1000.0)}s)")

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

    /**
     * Set progress callback for real-time transcription updates
     * @param callback Callback to receive progress updates (0-100), or null to clear
     */
    fun setProgressCallback(callback: WhisperProgressCallback?) {
        if (!libraryLoadSuccess) return

        try {
            nativeSetProgressCallback(callback)
            Log.d(TAG, "Progress callback ${if (callback == null) "cleared" else "set"}")
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting progress callback", e)
        }
    }

    /**
     * Set segment callback for real-time text streaming
     * @param callback Callback to receive transcribed segments, or null to clear
     */
    fun setSegmentCallback(callback: WhisperSegmentCallback?) {
        if (!libraryLoadSuccess) return

        try {
            nativeSetSegmentCallback(callback)
            Log.d(TAG, "Segment callback ${if (callback == null) "cleared" else "set"}")
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting segment callback", e)
        }
    }

    /**
     * Clear all callbacks (progress and segment)
     */
    fun clearCallbacks() {
        if (!libraryLoadSuccess) return

        try {
            nativeClearCallbacks()
            Log.d(TAG, "All callbacks cleared")
        } catch (e: Throwable) {
            Log.e(TAG, "Error clearing callbacks", e)
        }
    }
}
