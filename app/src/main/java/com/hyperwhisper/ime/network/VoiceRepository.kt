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

            // Check if we need two-step processing (transcription + post-processing)
            val needsTwoStepProcessing = needsTwoStepProcessing(voiceMode, apiSettings.provider)

            if (needsTwoStepProcessing) {
                // Step 1: Transcribe audio
                Log.d(TAG, "Using two-step processing: transcribe + post-process")
                val transcriptionResult = transcriptionStrategy.processAudio(
                    audioFile = audioFile,
                    audioBase64 = audioBase64,
                    voiceMode = voiceMode.copy(systemPrompt = "Transcribe the audio exactly as spoken."),
                    modelId = apiSettings.modelId
                )

                when (transcriptionResult) {
                    is ApiResult.Success -> {
                        // Step 2: Post-process the transcribed text with chat model
                        Log.d(TAG, "Transcription successful, applying post-processing")
                        return postProcessText(transcriptionResult.data, voiceMode, apiSettings)
                    }
                    is ApiResult.Error -> {
                        return transcriptionResult
                    }
                    else -> {
                        return ApiResult.Error("Unexpected result from transcription")
                    }
                }
            } else {
                // Single-step processing (direct strategy)
                val strategy = selectStrategy(voiceMode, apiSettings.provider)
                strategy.processAudio(
                    audioFile = audioFile,
                    audioBase64 = audioBase64,
                    voiceMode = voiceMode,
                    modelId = apiSettings.modelId
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio", e)
            ApiResult.Error("Processing failed: ${e.message}", e)
        }
    }

    /**
     * Determine if two-step processing is needed
     * (transcription-only models with transformation modes)
     */
    private fun needsTwoStepProcessing(
        voiceMode: VoiceMode,
        provider: ApiProvider
    ): Boolean {
        // Verbatim mode never needs post-processing
        if (voiceMode.id == "verbatim") return false

        // OpenRouter supports audio in chat completions, so single-step is fine
        if (provider == ApiProvider.OPENROUTER) return false

        // Gemini supports audio in chat completions
        if (provider == ApiProvider.GEMINI) return false

        // All other providers with transformation modes need two-step
        return true
    }

    /**
     * Post-process transcribed text using a chat model
     * Uses a simple text-to-text chat completion
     */
    private suspend fun postProcessText(
        transcribedText: String,
        voiceMode: VoiceMode,
        apiSettings: ApiSettings
    ): ApiResult<String> {
        return try {
            Log.d(TAG, "Post-processing text with system prompt: ${voiceMode.systemPrompt}")

            // Determine which chat model to use for post-processing
            val postProcessModel = when (apiSettings.provider) {
                ApiProvider.OPENAI -> "gpt-4o-mini" // Fast and cheap for post-processing
                ApiProvider.GROQ -> "llama-3.1-8b-instant" // Fast Groq model
                else -> "gpt-4o-mini" // Default fallback
            }

            Log.d(TAG, "Using model for post-processing: $postProcessModel")

            // Create text-only chat completion request
            val request = ChatCompletionRequest(
                model = postProcessModel,
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = listOf(
                            ContentPart.TextContent(text = voiceMode.systemPrompt)
                        )
                    ),
                    ChatMessage(
                        role = "user",
                        content = listOf(
                            ContentPart.TextContent(text = transcribedText)
                        )
                    )
                ),
                modalities = listOf("text") // Text-only output
            )

            // Make API call
            val response = chatCompletionStrategy.chatCompletionApiService.chatCompletion(request)

            if (response.isSuccessful) {
                val result = response.body()
                val processedText = result?.choices?.firstOrNull()?.message?.content
                if (processedText != null) {
                    Log.d(TAG, "Post-processing successful")
                    ApiResult.Success(processedText)
                } else {
                    Log.w(TAG, "No processed text in response, returning original")
                    ApiResult.Success(transcribedText)
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Post-processing API error: ${response.code()} - $errorBody")
                // On error, return original transcription
                Log.w(TAG, "Post-processing failed, returning original transcription")
                ApiResult.Success(transcribedText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in post-processing, returning original text", e)
            // On exception, return original transcription
            ApiResult.Success(transcribedText)
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
