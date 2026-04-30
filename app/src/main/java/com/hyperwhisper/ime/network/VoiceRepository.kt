package com.hyperwhisper.network

import android.util.Log
import com.hyperwhisper.audio.AudioRecorderManager
import com.hyperwhisper.data.*
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class VoiceRepository @Inject constructor(
    private val audioRecorderManager: AudioRecorderManager,
    private val transcriptionStrategy: TranscriptionStrategy,
    private val chatCompletionStrategy: ChatCompletionStrategy,
    private val settingsRepository: SettingsRepository,
    private val apiCallLogRepository: ApiCallLogRepository,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "VoiceRepository"
    }

    /**
     * Create a dynamic LLM API client based on LLM configuration
     */
    private fun createLlmApiService(llmConfig: LlmConfig): ChatCompletionApiService {
        // Create auth interceptor for LLM
        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()

            // Add authorization header if required
            if (llmConfig.requiresAuth && llmConfig.apiKey.isNotEmpty()) {
                when (llmConfig.provider) {
                    com.hyperwhisper.data.LlmProvider.ANTHROPIC -> {
                        // Anthropic uses x-api-key header
                        requestBuilder.addHeader("x-api-key", llmConfig.apiKey)
                        requestBuilder.addHeader("anthropic-version", "2023-06-01")
                    }
                    else -> {
                        // OpenAI-compatible providers use Bearer token
                        requestBuilder.addHeader("Authorization", "Bearer ${llmConfig.apiKey}")
                    }
                }
            }

            requestBuilder.addHeader("Content-Type", "application/json")
            val request = requestBuilder.build()
            chain.proceed(request)
        }

        // Create OkHttp client for LLM
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(300, TimeUnit.SECONDS)
            .build()

        // Create Retrofit instance
        val baseUrl = llmConfig.getBaseUrl()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return retrofit.create(ChatCompletionApiService::class.java)
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
        val processingStartTime = System.currentTimeMillis()
        val audioFileSizeBytes = audioFile.length()
        return try {

            Log.d(TAG, "=== PROCESSING STARTED ===")
            Log.d(TAG, "File: ${audioFile.name}")
            Log.d(TAG, "File size: ${audioFileSizeBytes / 1024} KB")
            Log.d(TAG, "Provider: ${apiSettings.provider.displayName}")
            Log.d(TAG, "Model: ${apiSettings.modelId}")
            Log.d(TAG, "Voice mode: ${voiceMode.name}")
            Log.d(TAG, "Requires auth: ${apiSettings.getCurrentRequiresAuth()}")

            // Calculate audio duration in seconds (approximate based on file size and format)
            // For m4a at 128kbps: ~16KB per second
            val audioDurationSeconds = calculateAudioDuration(audioFile)
            Log.d(TAG, "Audio duration: $audioDurationSeconds seconds (${String.format("%.1f", audioDurationSeconds / 60.0)} minutes)")

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
                val transcriptionStartTime = System.currentTimeMillis()
                val transcriptionResult = transcriptionStrategy.processAudio(
                    audioFile = audioFile,
                    audioBase64 = audioBase64,
                    voiceMode = voiceMode.copy(systemPrompt = "Transcribe the audio exactly as spoken."),
                    modelId = apiSettings.modelId
                )
                val transcriptionTimeMs = System.currentTimeMillis() - transcriptionStartTime
                Log.d(TAG, "Transcription completed in ${transcriptionTimeMs}ms")

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
                            transcriptionTokens = transcriptionResult.processingInfo?.transcriptionTokens,
                            transcriptionTimeMs = transcriptionTimeMs,
                            audioFileSizeBytes = audioFileSizeBytes,
                            processingStartTime = processingStartTime
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

                Log.d(TAG, "Using single-step processing with strategy: $strategyName")
                val apiCallStartTime = System.currentTimeMillis()
                val result = strategy.processAudio(
                    audioFile = audioFile,
                    audioBase64 = audioBase64,
                    voiceMode = voiceMode,
                    modelId = apiSettings.modelId
                )
                val apiCallTimeMs = System.currentTimeMillis() - apiCallStartTime

                // Add processing info for single-step
                when (result) {
                    is ApiResult.Success -> {
                        val totalProcessingTimeMs = System.currentTimeMillis() - processingStartTime

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
                            postProcessingTokens = null,
                            processingTimeMs = totalProcessingTimeMs,
                            transcriptionTimeMs = apiCallTimeMs,
                            postProcessingTimeMs = null,
                            audioFileSizeBytes = audioFileSizeBytes,
                            timestamp = processingStartTime
                        )

                        // Log comprehensive metrics
                        Log.d(TAG, "=== PROCESSING COMPLETE ===")
                        Log.d(TAG, "Total time: ${totalProcessingTimeMs}ms (${String.format("%.2f", totalProcessingTimeMs / 1000.0)}s)")
                        Log.d(TAG, "API call time: ${apiCallTimeMs}ms")
                        Log.d(TAG, "Processing speed: ${String.format("%.2fx", audioDurationSeconds / (totalProcessingTimeMs / 1000.0))} realtime")
                        result.processingInfo?.transcriptionTokens?.let { tokens ->
                            Log.d(TAG, "Tokens - Input: ${tokens.promptTokens}, Output: ${tokens.completionTokens}, Total: ${tokens.totalTokens}")
                        }
                        Log.d(TAG, "============================")

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

                        // Log API call
                        apiCallLogRepository.addLog(
                            ApiCallLog(
                                provider = apiSettings.provider,
                                modelId = apiSettings.modelId,
                                requestType = strategyName,
                                inputSize = audioFileSizeBytes,
                                responseText = result.data.take(100), // First 100 chars
                                success = true,
                                errorMessage = null,
                                durationMs = totalProcessingTimeMs,
                                tokenUsage = result.processingInfo?.transcriptionTokens
                            )
                        )

                        ApiResult.Success(result.data, processingInfo)
                    }
                    else -> result
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio", e)

            // Log API call error
            apiCallLogRepository.addLog(
                ApiCallLog(
                    provider = apiSettings.provider,
                    modelId = apiSettings.modelId,
                    requestType = "transcription",
                    inputSize = audioFile.length(),
                    responseText = null,
                    success = false,
                    errorMessage = e.message,
                    durationMs = System.currentTimeMillis() - processingStartTime
                )
            )

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
        // If LLM is disabled (NONE), no post-processing
        if (apiSettings.llmConfig.provider == com.hyperwhisper.data.LlmProvider.NONE) {
            return false
        }

        // Translation is only needed if output language is set AND different from input
        // If both are the same (e.g., both "en"), no translation is needed
        val needsTranslation = apiSettings.outputLanguage.isNotEmpty() &&
            apiSettings.outputLanguage != apiSettings.inputLanguage

        // OpenRouter supports audio in chat completions AND translation in one step
        if (apiSettings.provider == ApiProvider.OPENROUTER) return false

        // Gemini supports audio in chat completions AND translation in one step
        if (apiSettings.provider == ApiProvider.GEMINI) return false

        // Antigravity provider is OpenAI-compatible chat endpoint with audio support
        if (apiSettings.provider == ApiProvider.ANTIGRAVITY) return false

        // Hugging Face is text-only - requires two-step for all audio input
        if (apiSettings.provider == ApiProvider.HUGGINGFACE) return true

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
        transcriptionTokens: TokenUsage?,
        transcriptionTimeMs: Long,
        audioFileSizeBytes: Long,
        processingStartTime: Long
    ): ApiResult<String> {
        return try {
            // Check if LLM processing is disabled
            if (apiSettings.llmConfig.provider == com.hyperwhisper.data.LlmProvider.NONE) {
                Log.d(TAG, "LLM post-processing disabled (NONE selected), returning original transcription")
                return ApiResult.Success(transcribedText)
            }

            // Build system prompt with translation if needed
            val systemPrompt = buildSystemPrompt(voiceMode.systemPrompt, apiSettings.outputLanguage)
            Log.d(TAG, "Starting post-processing with system prompt: $systemPrompt")

            // Use configured LLM settings
            val llmConfig = apiSettings.llmConfig
            val postProcessModel = llmConfig.modelId

            Log.d(TAG, "Using LLM for post-processing: ${llmConfig.provider.displayName}, model: $postProcessModel")
            Log.d(TAG, "LLM endpoint: ${llmConfig.getBaseUrl()}")
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

            // Create dynamic LLM API client and make API call
            val postProcessStartTime = System.currentTimeMillis()
            val llmApiService = createLlmApiService(llmConfig)
            val response = llmApiService.chatCompletion(request)
            val postProcessTimeMs = System.currentTimeMillis() - postProcessStartTime

            if (response.isSuccessful) {
                val result = response.body()
                val processedText = result?.choices?.firstOrNull()?.message?.content
                val postProcessingTokens = result?.usage

                if (processedText != null) {
                    val totalProcessingTimeMs = System.currentTimeMillis() - processingStartTime

                    Log.d(TAG, "Post-processing completed in ${postProcessTimeMs}ms")
                    Log.d(TAG, "=== TWO-STEP PROCESSING COMPLETE ===")
                    Log.d(TAG, "Total time: ${totalProcessingTimeMs}ms (${String.format("%.2f", totalProcessingTimeMs / 1000.0)}s)")
                    Log.d(TAG, "  - Transcription: ${transcriptionTimeMs}ms")
                    Log.d(TAG, "  - Post-processing: ${postProcessTimeMs}ms")
                    Log.d(TAG, "Processing speed: ${String.format("%.2fx", audioDurationSeconds / (totalProcessingTimeMs / 1000.0))} realtime")
                    transcriptionTokens?.let { tokens ->
                        Log.d(TAG, "Transcription tokens - Input: ${tokens.promptTokens}, Output: ${tokens.completionTokens}, Total: ${tokens.totalTokens}")
                    }
                    postProcessingTokens?.let { tokens ->
                        Log.d(TAG, "Post-processing tokens - Input: ${tokens.promptTokens}, Output: ${tokens.completionTokens}, Total: ${tokens.totalTokens}")
                    }
                    Log.d(TAG, "==================================")

                    // Create processing info
                    val processingInfo = ProcessingInfo(
                        processingMode = "two-step",
                        strategy = "transcription + ${llmConfig.provider.displayName}",
                        transcriptionModel = transcriptionModel,
                        postProcessingModel = "${llmConfig.provider.displayName}:$postProcessModel",
                        translationEnabled = apiSettings.outputLanguage.isNotEmpty(),
                        translationTarget = if (apiSettings.outputLanguage.isNotEmpty()) getLanguageName(apiSettings.outputLanguage) else null,
                        originalTranscription = transcribedText,
                        voiceModeName = voiceMode.name,
                        systemPrompt = systemPrompt,
                        audioDurationSeconds = audioDurationSeconds,
                        transcriptionTokens = transcriptionTokens,
                        postProcessingTokens = postProcessingTokens,
                        processingTimeMs = totalProcessingTimeMs,
                        transcriptionTimeMs = transcriptionTimeMs,
                        postProcessingTimeMs = postProcessTimeMs,
                        audioFileSizeBytes = audioFileSizeBytes,
                        timestamp = processingStartTime
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
     * - For "Verbatim" mode with OpenAI/Groq: Use Transcription Strategy
     * - For transformation modes (Polite, Casual, etc.): Use Chat Completion Strategy
     * - For OpenRouter: Always use Chat Completion Strategy
     * - For Gemini: Always use Chat Completion Strategy (supports audio natively)
     * - For Hugging Face: Always use Chat Completion Strategy (text-only models)
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
            // Gemini always uses chat completion (supports audio natively)
            provider == ApiProvider.GEMINI -> {
                Log.d(TAG, "Selected ChatCompletionStrategy (Gemini)")
                chatCompletionStrategy
            }
            // Antigravity uses OpenAI-compatible chat completion with OAuth-backed quota
            provider == ApiProvider.ANTIGRAVITY -> {
                Log.d(TAG, "Selected ChatCompletionStrategy (Antigravity)")
                chatCompletionStrategy
            }
            // Hugging Face always uses chat completion (text-only LLMs)
            provider == ApiProvider.HUGGINGFACE -> {
                Log.d(TAG, "Selected ChatCompletionStrategy (HuggingFace - text-only)")
                chatCompletionStrategy
            }
            // Verbatim mode with transcription-style providers uses transcription
            voiceMode.id == "verbatim" && (provider == ApiProvider.OPENAI || provider == ApiProvider.GROQ || provider == ApiProvider.SELFHOSTED_WHISPER) -> {
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
