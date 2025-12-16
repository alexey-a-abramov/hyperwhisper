package com.hyperwhisper.network

import android.util.Log
import com.hyperwhisper.audio.AudioRecorderManager
import com.hyperwhisper.data.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceRepository @Inject constructor(
    private val audioRecorderManager: AudioRecorderManager,
    private val transcriptionStrategy: TranscriptionStrategy,
    private val chatCompletionStrategy: ChatCompletionStrategy
) {
    companion object {
        private const val TAG = "VoiceRepository"
    }

    /**
     * Process recorded audio based on voice mode and API provider
     * Automatically selects the appropriate strategy
     */
    suspend fun processAudio(
        audioFile: File,
        voiceMode: VoiceMode,
        apiSettings: ApiSettings
    ): ApiResult<String> {
        return try {
            Log.d(TAG, "Processing audio with mode: ${voiceMode.name}, provider: ${apiSettings.provider}")

            // Convert audio to base64
            val base64Result = audioRecorderManager.audioFileToBase64(audioFile)
            if (base64Result.isFailure) {
                return ApiResult.Error("Failed to encode audio: ${base64Result.exceptionOrNull()?.message}")
            }
            val audioBase64 = base64Result.getOrNull() ?: ""

            // Select strategy based on mode and provider
            val strategy = selectStrategy(voiceMode, apiSettings.provider)

            // Process audio using selected strategy
            strategy.processAudio(
                audioFile = audioFile,
                audioBase64 = audioBase64,
                voiceMode = voiceMode,
                modelId = apiSettings.modelId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio", e)
            ApiResult.Error("Processing failed: ${e.message}", e)
        }
    }

    /**
     * Select appropriate strategy based on voice mode and API provider
     *
     * Logic:
     * - For "Verbatim" mode with OpenAI/Groq: Use Transcription Strategy
     * - For transformation modes (Polite, Casual, etc.): Use Chat Completion Strategy
     * - For OpenRouter: Always use Chat Completion Strategy
     */
    private fun selectStrategy(
        voiceMode: VoiceMode,
        provider: ApiProvider
    ): AudioProcessingStrategy {
        return when {
            // OpenRouter always uses chat completion
            provider == ApiProvider.OPENROUTER -> {
                Log.d(TAG, "Selected ChatCompletionStrategy (OpenRouter)")
                chatCompletionStrategy
            }
            // Verbatim mode with OpenAI uses transcription
            voiceMode.id == "verbatim" && provider == ApiProvider.OPENAI -> {
                Log.d(TAG, "Selected TranscriptionStrategy (Verbatim)")
                transcriptionStrategy
            }
            // All transformations use chat completion
            else -> {
                Log.d(TAG, "Selected ChatCompletionStrategy (Transformation)")
                chatCompletionStrategy
            }
        }
    }

    /**
     * Start audio recording
     */
    suspend fun startRecording(): Result<Unit> {
        return audioRecorderManager.startRecording()
    }

    /**
     * Stop audio recording and return file
     */
    suspend fun stopRecording(): Result<File> {
        return audioRecorderManager.stopRecording()
    }

    /**
     * Cancel recording
     */
    suspend fun cancelRecording() {
        audioRecorderManager.cancelRecording()
    }

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean {
        return audioRecorderManager.isCurrentlyRecording()
    }
}
