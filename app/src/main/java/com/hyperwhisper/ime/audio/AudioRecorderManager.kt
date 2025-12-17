package com.hyperwhisper.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import com.hyperwhisper.utils.TraceLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecorderManager @Inject constructor(
    private val context: Context
) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFile: File? = null
    private var isRecording = false

    companion object {
        private const val TAG = "AudioRecorderManager"
        private const val SAMPLE_RATE = 16000
        private const val BIT_RATE = 128000
    }

    /**
     * Start recording audio
     */
    suspend fun startRecording(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isRecording) {
                TraceLogger.trace("AudioRecorder", "Already recording - ignoring start request")
                return@withContext Result.failure(IllegalStateException("Already recording"))
            }

            TraceLogger.trace("AudioRecorder", "Starting audio recording session")

            // Create temp file
            val audioFile = File.createTempFile(
                "audio_${System.currentTimeMillis()}",
                ".m4a",
                context.cacheDir
            )
            currentAudioFile = audioFile
            TraceLogger.trace("AudioRecorder", "Created temp file: ${audioFile.absolutePath}")

            // Try VOICE_RECOGNITION first (works better for keyboards/background services)
            // Fall back to MIC if that fails
            val audioSources = listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
                MediaRecorder.AudioSource.MIC to "MIC",
                MediaRecorder.AudioSource.CAMCORDER to "CAMCORDER"
            )

            var lastException: Exception? = null

            for ((audioSource, sourceName) in audioSources) {
                try {
                    TraceLogger.trace("AudioRecorder", "Trying audio source: $sourceName")

                    // Initialize MediaRecorder
                    mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(context)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }.apply {
                        setAudioSource(audioSource)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioSamplingRate(SAMPLE_RATE)
                        setAudioEncodingBitRate(BIT_RATE)
                        setOutputFile(audioFile.absolutePath)

                        prepare()
                        start()
                    }

                    isRecording = true
                    Log.d(TAG, "Recording started with $sourceName: ${audioFile.absolutePath}")
                    TraceLogger.trace("AudioRecorder", "Recording started successfully with $sourceName")
                    return@withContext Result.success(Unit)

                } catch (e: Exception) {
                    Log.w(TAG, "Failed to start recording with $sourceName: ${e.message}")
                    TraceLogger.trace("AudioRecorder", "Failed with $sourceName: ${e.message}")
                    lastException = e
                    mediaRecorder?.release()
                    mediaRecorder = null
                    // Continue to next audio source
                }
            }

            // All audio sources failed
            val errorMessage = "Failed to access microphone with any audio source. " +
                "Last error: ${lastException?.message}. " +
                "Please ensure microphone permission is granted and no other app is using it."

            Log.e(TAG, errorMessage, lastException)
            TraceLogger.error("AudioRecorder", errorMessage, lastException)

            cleanup()
            Result.failure(Exception(errorMessage, lastException))

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting recording", e)
            TraceLogger.error("AudioRecorder", "Unexpected error starting recording", e)
            cleanup()
            Result.failure(e)
        }
    }

    /**
     * Stop recording and return the audio file
     */
    suspend fun stopRecording(): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!isRecording) {
                return@withContext Result.failure(IllegalStateException("Not recording"))
            }

            mediaRecorder?.apply {
                try {
                    stop()
                    release()
                } catch (e: RuntimeException) {
                    Log.e(TAG, "Error stopping recording", e)
                }
            }
            mediaRecorder = null
            isRecording = false

            val file = currentAudioFile
            if (file != null && file.exists() && file.length() > 0) {
                Log.d(TAG, "Recording stopped: ${file.absolutePath}, size: ${file.length()} bytes")
                Result.success(file)
            } else {
                Result.failure(IOException("Audio file is empty or doesn't exist"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            cleanup()
            Result.failure(e)
        }
    }

    /**
     * Cancel recording and cleanup
     */
    suspend fun cancelRecording() = withContext(Dispatchers.IO) {
        try {
            if (isRecording) {
                mediaRecorder?.apply {
                    try {
                        stop()
                    } catch (e: RuntimeException) {
                        Log.e(TAG, "Error canceling recording", e)
                    }
                    release()
                }
                mediaRecorder = null
                isRecording = false
            }
            cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling recording", e)
        }
    }

    /**
     * Convert audio file to Base64 string
     */
    suspend fun audioFileToBase64(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                return@withContext Result.failure(IOException("File doesn't exist: ${file.absolutePath}"))
            }

            val bytes = file.readBytes()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Log.d(TAG, "Converted audio to base64, size: ${base64.length} chars")
            Result.success(base64)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting audio to base64", e)
            Result.failure(e)
        }
    }

    /**
     * Get audio format based on file extension
     */
    fun getAudioFormat(file: File): String {
        return when (file.extension.lowercase()) {
            "m4a" -> "mp4"
            "wav" -> "wav"
            "mp3" -> "mp3"
            else -> "mp4" // default to mp4
        }
    }

    /**
     * Cleanup temp files
     */
    private fun cleanup() {
        currentAudioFile?.let { file ->
            try {
                if (file.exists()) {
                    file.delete()
                    Log.d(TAG, "Cleaned up audio file: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up audio file", e)
            }
        }
        currentAudioFile = null
    }

    /**
     * Release resources
     */
    fun release() {
        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
        cleanup()
    }

    fun isCurrentlyRecording(): Boolean = isRecording
}
