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
    private val chatCompletionStrategy: ChatCompletionStrategy,
    private val localWhisperStrategy: LocalWhisperStrategy,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "VoiceRepository"
    }

    /**
     * Get recording duration flow
     */
    fun getRecordingDuration() = audioRecorderManager.recordingDuration

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

            // Calculate audio duration in seconds (approximate based on file size and format)
            // For m4a at 128kbps: ~16KB per second
            val audioDurationSeconds = calculateAudioDuration(audioFile)
            Log.d(TAG, "Audio duration: $audioDurationSeconds seconds")

            // Convert audio to base64
            val base64Result = audioRecorderManager.audioFileToBase64(audioFile)
            if (base64Result.isFailure) {
                return ApiResult.Error("Failed to encode audio: ${base64Result.exceptionOrNull()?.message}")
            }
            val audioBase64 = base64Result.getOrNull() ?: ""

            // Check if we need two-step processing (transcription + post-processing)
            val needsTwoStepProcessing = needsTwoStepProcessing(voiceMode, apiSettings)

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
                        val originalTranscription = transcriptionResult.data
                        return postProcessText(
                            transcribedText = originalTranscription,
                            voiceMode = voiceMode,
                            apiSettings = apiSettings,
                            transcriptionModel = apiSettings.modelId,
                            audioDurationSeconds = audioDurationSeconds,
                            transcriptionTokens = transcriptionResult.processingInfo?.transcriptionTokens
                        )
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
                val strategyName = if (strategy is TranscriptionStrategy) "transcription" else "chat-completion"
                val systemPrompt = buildSystemPrompt(voiceMode.systemPrompt, apiSettings.outputLanguage)

                val result = strategy.processAudio(
                    audioFile = audioFile,
                    audioBase64 = audioBase64,
                    voiceMode = voiceMode,
                    modelId = apiSettings.modelId
                )

                // Add processing info for single-step
                when (result) {
                    is ApiResult.Success -> {
                        val processingInfo = ProcessingInfo(
                            processingMode = "single-step",
                            strategy = strategyName,
                            transcriptionModel = apiSettings.modelId,
                            postProcessingModel = null,
                            translationEnabled = apiSettings.outputLanguage.isNotEmpty(),
                            translationTarget = if (apiSettings.outputLanguage.isNotEmpty()) getLanguageName(apiSettings.outputLanguage) else null,
                            originalTranscription = null,
                            voiceModeName = voiceMode.name,
                            systemPrompt = systemPrompt,
                            audioDurationSeconds = audioDurationSeconds,
                            transcriptionTokens = result.processingInfo?.transcriptionTokens,
                            postProcessingTokens = null
                        )

                        // Record usage statistics
                        result.processingInfo?.transcriptionTokens?.let { tokens ->
                            settingsRepository.recordUsage(
                                modelId = apiSettings.modelId,
                                inputTokens = tokens.promptTokens ?: 0,
                                outputTokens = tokens.completionTokens ?: 0,
                                totalTokens = tokens.totalTokens ?: 0,
                                audioDurationSeconds = audioDurationSeconds
                            )
                        }

                        ApiResult.Success(result.data, processingInfo)
                    }
                    else -> result
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio", e)
            ApiResult.Error("Processing failed: ${e.message}", e)
        }
    }

    /**
     * Determine if two-step processing is needed
     * (transcription-only models with transformation modes or translation)
     */
    private fun needsTwoStepProcessing(
        voiceMode: VoiceMode,
        apiSettings: ApiSettings
    ): Boolean {
        // Local processing doesn't support two-step
        if (apiSettings.provider == ApiProvider.LOCAL) return false

        // Translation is only needed if output language is set AND different from input
        // If both are the same (e.g., both "en"), no translation is needed
        val needsTranslation = apiSettings.outputLanguage.isNotEmpty() &&
            apiSettings.outputLanguage != apiSettings.inputLanguage

        // OpenRouter supports audio in chat completions AND translation in one step
        if (apiSettings.provider == ApiProvider.OPENROUTER) return false

        // Gemini supports audio in chat completions AND translation in one step
        if (apiSettings.provider == ApiProvider.GEMINI) return false

        // Verbatim mode only needs post-processing if translation is required
        if (voiceMode.id == "verbatim") return needsTranslation

        // All other providers with transformation modes need two-step
        return true
    }

    /**
     * Get language name from ISO code for translation instruction
     */
    private fun getLanguageName(languageCode: String): String {
        val language = SUPPORTED_LANGUAGES.find { it.code == languageCode }
        return language?.name ?: languageCode.uppercase()
    }

    /**
     * Build system prompt with optional translation instruction
     */
    private fun buildSystemPrompt(basePrompt: String, outputLanguage: String): String {
        return if (outputLanguage.isNotEmpty()) {
            val languageName = getLanguageName(outputLanguage)
            "$basePrompt\n\nIMPORTANT: Translate the output to $languageName. Return ONLY the $languageName translation, do not include the original text."
        } else {
            basePrompt
        }
    }

    /**
     * Post-process transcribed text using a chat model
     * Uses a simple text-to-text chat completion
     */
    private suspend fun postProcessText(
        transcribedText: String,
        voiceMode: VoiceMode,
        apiSettings: ApiSettings,
        transcriptionModel: String,
        audioDurationSeconds: Double,
        transcriptionTokens: TokenUsage?
    ): ApiResult<String> {
        return try {
            // Build system prompt with translation if needed
            val systemPrompt = buildSystemPrompt(voiceMode.systemPrompt, apiSettings.outputLanguage)
            Log.d(TAG, "Post-processing text with system prompt: $systemPrompt")

            // Determine which chat model to use for post-processing
            val postProcessModel = "gpt-4o-mini"

            Log.d(TAG, "Using model for post-processing: $postProcessModel")
            if (apiSettings.outputLanguage.isNotEmpty()) {
                Log.d(TAG, "Translation enabled: output language = ${getLanguageName(apiSettings.outputLanguage)}")
            }

            // Create text-only chat completion request
            val request = ChatCompletionRequest(
                model = postProcessModel,
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = listOf(
                            ContentPart.TextContent(text = systemPrompt)
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
                val postProcessingTokens = result?.usage

                if (processedText != null) {
                    Log.d(TAG, "Post-processing successful")

                    // Create processing info
                    val processingInfo = ProcessingInfo(
                        processingMode = "two-step",
                        strategy = "transcription + chat-completion",
                        transcriptionModel = transcriptionModel,
                        postProcessingModel = postProcessModel,
                        translationEnabled = apiSettings.outputLanguage.isNotEmpty(),
                        translationTarget = if (apiSettings.outputLanguage.isNotEmpty()) getLanguageName(apiSettings.outputLanguage) else null,
                        originalTranscription = transcribedText,
                        voiceModeName = voiceMode.name,
                        systemPrompt = systemPrompt,
                        audioDurationSeconds = audioDurationSeconds,
                        transcriptionTokens = transcriptionTokens,
                        postProcessingTokens = postProcessingTokens
                    )

                    // Record usage statistics for both models
                    transcriptionTokens?.let { tokens ->
                        settingsRepository.recordUsage(
                            modelId = transcriptionModel,
                            inputTokens = tokens.promptTokens ?: 0,
                            outputTokens = tokens.completionTokens ?: 0,
                            totalTokens = tokens.totalTokens ?: 0,
                            audioDurationSeconds = audioDurationSeconds
                        )
                    }

                    postProcessingTokens?.let { tokens ->
                        settingsRepository.recordUsage(
                            modelId = postProcessModel,
                            inputTokens = tokens.promptTokens ?: 0,
                            outputTokens = tokens.completionTokens ?: 0,
                            totalTokens = tokens.totalTokens ?: 0,
                            audioDurationSeconds = 0.0 // Don't double-count audio duration
                        )
                    }

                    ApiResult.Success(processedText, processingInfo)
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
     * - For LOCAL provider: Use Local Whisper Strategy (whisper.cpp)
     * - For "Verbatim" mode with OpenAI/Groq: Use Transcription Strategy
     * - For transformation modes (Polite, Casual, etc.): Use Chat Completion Strategy
     * - For OpenRouter: Always use Chat Completion Strategy
     */
    private fun selectStrategy(
        voiceMode: VoiceMode,
        provider: ApiProvider
    ): AudioProcessingStrategy {
        return when {
            // LOCAL provider uses whisper.cpp
            provider == ApiProvider.LOCAL -> {
                Log.d(TAG, "Selected LocalWhisperStrategy (On-Device)")
                localWhisperStrategy
            }
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

    /**
     * Calculate audio duration in seconds from file
     * Approximation based on file size and bitrate
     */
    private fun calculateAudioDuration(audioFile: File): Double {
        return try {
            // For m4a at 128kbps (16KB/s), approximate duration
            val fileSizeBytes = audioFile.length()
            val durationSeconds = fileSizeBytes / 16000.0 // ~16KB per second at 128kbps
            durationSeconds
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating audio duration", e)
            0.0
        }
    }
}
