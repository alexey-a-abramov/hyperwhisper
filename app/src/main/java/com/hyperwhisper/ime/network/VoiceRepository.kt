package com.hyperwhisper.network

import android.util.Log
import com.hyperwhisper.audio.AudioRecorderManager
import com.hyperwhisper.data.*
import com.hyperwhisper.utils.TraceLogger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceRepository @Inject constructor(
    private val audioRecorderManager: AudioRecorderManager,
    private val transcriptionStrategy: TranscriptionStrategy,
    private val gemma: com.hyperwhisper.ime.llm.GemmaInferenceEngine,
    private val settingsRepository: SettingsRepository,
    private val apiCallLogRepository: ApiCallLogRepository,
    private val llmServiceFactory: LlmServiceFactory,
    private val processingRouter: ProcessingRouter,
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
        val processingStartTime = System.currentTimeMillis()
        val audioFileSizeBytes = audioFile.length()

        // Pre-flight: fail fast with a clear message rather than a generic API error.
        validateConfig(voiceMode, apiSettings)?.let { msg ->
            TraceLogger.error(TAG, "Config validation failed: $msg")
            apiCallLogRepository.addLog(
                ApiCallLog(
                    provider = apiSettings.provider,
                    modelId = apiSettings.modelId,
                    requestType = "config-validation",
                    inputSize = audioFileSizeBytes,
                    responseText = null,
                    success = false,
                    errorMessage = msg,
                    durationMs = 0
                )
            )
            return ApiResult.Error(msg)
        }

        return try {

            TraceLogger.trace(TAG, "=== PROCESSING STARTED ===")
            TraceLogger.trace(TAG, "File: ${audioFile.name} (${audioFileSizeBytes / 1024} KB)")
            TraceLogger.trace(TAG, "Provider: ${apiSettings.provider.displayName}, Model: ${apiSettings.modelId}")
            TraceLogger.trace(TAG, "Voice mode: ${voiceMode.name}, RequiresAuth: ${apiSettings.getCurrentRequiresAuth()}")
            logLlmConfig(apiSettings.llmConfig)

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
            val needsTwoStepProcessing = processingRouter.needsTwoStepProcessing(voiceMode, apiSettings)

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
                val strategy = processingRouter.selectStrategy(voiceMode, apiSettings.provider)
                val strategyName = when (strategy) {
                    is TranscriptionStrategy -> "transcription"
                    is LocalProcessingStrategy -> "local"
                    else -> "chat-completion"
                }
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
                            translationTarget = if (apiSettings.outputLanguage.isNotEmpty()) LanguageNames.displayNameFor(apiSettings.outputLanguage) else null,
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

                        // Record usage statistics. Single-step path → final output is
                        // result.data; count its chars/bytes once here.
                        val outputChars = result.data.length.toLong()
                        val outputBytes = result.data.toByteArray(Charsets.UTF_8).size.toLong()
                        result.processingInfo?.transcriptionTokens?.let { tokens ->
                            settingsRepository.recordUsage(
                                modelId = apiSettings.modelId,
                                inputTokens = tokens.promptTokens ?: 0,
                                outputTokens = tokens.completionTokens ?: 0,
                                totalTokens = tokens.totalTokens ?: 0,
                                audioDurationSeconds = audioDurationSeconds,
                                outputCharacters = outputChars,
                                outputBytes = outputBytes
                            )
                        } ?: settingsRepository.recordUsage(
                            // Provider returned no token usage info; still record chars
                            // so the totals are accurate.
                            modelId = apiSettings.modelId,
                            inputTokens = 0,
                            outputTokens = 0,
                            totalTokens = 0,
                            audioDurationSeconds = audioDurationSeconds,
                            outputCharacters = outputChars,
                            outputBytes = outputBytes
                        )

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
            TraceLogger.error(TAG, "Error processing audio (provider=${apiSettings.provider.name}, model=${apiSettings.modelId})", e)

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

            ApiResult.Error(ErrorMessageFormatter.friendlyMessage(e), e)
        } catch (t: Throwable) {
            // Catches OOM/UnsatisfiedLinkError etc. from local model inference paths.
            // We log and convert to a graceful error rather than letting it crash the IME.
            TraceLogger.error(TAG, "Fatal error in audio processing — converted to ApiResult.Error", t)
            ApiResult.Error("Inference failed: ${t.javaClass.simpleName}: ${t.message ?: "no message"}")
        }
    }

    /**
     * Validate provider/LLM/model setup. Returns null when OK, or a user-facing
     * error string. Never logs the API key.
     */
    private fun validateConfig(voiceMode: VoiceMode, s: ApiSettings): String? {
        if (s.modelId.isBlank()) return "Transcription model is not configured. Open Settings → Transcription."
        val baseUrl = s.getCurrentBaseUrl()
        if (baseUrl.isBlank() && s.provider != ApiProvider.SELFHOSTED_WHISPER) {
            return "Provider base URL is empty for ${s.provider.displayName}."
        }
        if (s.getCurrentRequiresAuth() && s.getCurrentApiKey().isBlank()) {
            return "API key for ${s.provider.displayName} is missing."
        }

        val llm = s.llmConfig
        val needsLlm = llm.provider != LlmProvider.NONE &&
            (voiceMode.id != "verbatim" || s.outputLanguage.isNotEmpty())
        if (needsLlm) {
            if (llm.modelId.isBlank()) return "LLM model is not set for ${llm.provider.displayName}."
            if (llm.getBaseUrl().isBlank()) return "LLM base URL is empty for ${llm.provider.displayName}."
            if (llm.requiresAuth && llm.apiKey.isBlank()) {
                return "LLM API key for ${llm.provider.displayName} is missing."
            }
        }
        return null
    }

    private fun logLlmConfig(llm: LlmConfig) {
        // Never log the key itself; only whether one is present.
        TraceLogger.trace(
            TAG,
            "LLM config: provider=${llm.provider.name}, model=${llm.modelId}, " +
                "baseUrl=${llm.getBaseUrl()}, requiresAuth=${llm.requiresAuth}, " +
                "hasKey=${llm.apiKey.isNotBlank()}"
        )
    }

    /**
     * Build system prompt with optional translation instruction
     */
    private fun buildSystemPrompt(basePrompt: String, outputLanguage: String): String {
        return if (outputLanguage.isNotEmpty()) {
            val languageName = LanguageNames.displayNameFor(outputLanguage)
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
                Log.d(TAG, "Translation enabled: output language = ${LanguageNames.displayNameFor(apiSettings.outputLanguage)}")
            }

            // LOCAL_GEMMA → in-process MediaPipe path. Bypass the HTTP client
            // entirely; no separate llama.cpp / ollama server required.
            if (llmConfig.provider == com.hyperwhisper.data.LlmProvider.LOCAL_GEMMA) {
                val localPath = apiSettings.localModelSettings.gemmaModelPath
                if (localPath.isBlank() || !java.io.File(localPath).exists()) {
                    Log.w(TAG, "Local Gemma path missing or file not found: '$localPath' — falling back to raw transcription")
                    return ApiResult.Success(transcribedText)
                }
                val postProcessStartTime = System.currentTimeMillis()
                val rewritten = try {
                    gemma.rewrite(
                        modelPath = localPath,
                        systemPrompt = systemPrompt,
                        userText = transcribedText
                    )
                } catch (t: Throwable) {
                    Log.w(TAG, "Local Gemma post-processing failed; returning raw transcription", t)
                    return ApiResult.Success(transcribedText)
                }
                val postProcessTimeMs = System.currentTimeMillis() - postProcessStartTime
                val totalProcessingTimeMs = System.currentTimeMillis() - processingStartTime
                Log.d(TAG, "Local Gemma post-processing done in ${postProcessTimeMs}ms")

                // Record stats for the in-process Gemma session. No tokens (MediaPipe
                // doesn't surface them), but audio duration + final-output chars/bytes
                // belong on the books just like the HTTP two-step path.
                val finalText = rewritten.ifBlank { transcribedText }
                settingsRepository.recordUsage(
                    modelId = "LocalGemma:${java.io.File(localPath).name}",
                    inputTokens = 0,
                    outputTokens = 0,
                    totalTokens = 0,
                    audioDurationSeconds = audioDurationSeconds,
                    outputCharacters = finalText.length.toLong(),
                    outputBytes = finalText.toByteArray(Charsets.UTF_8).size.toLong()
                )

                val info = ProcessingInfo(
                    processingMode = "two-step",
                    strategy = "transcription + Local Gemma (in-proc)",
                    transcriptionModel = transcriptionModel,
                    postProcessingModel = "LocalGemma:${java.io.File(localPath).name}",
                    translationEnabled = apiSettings.outputLanguage.isNotEmpty(),
                    translationTarget = if (apiSettings.outputLanguage.isNotEmpty()) LanguageNames.displayNameFor(apiSettings.outputLanguage) else null,
                    originalTranscription = transcribedText,
                    voiceModeName = voiceMode.name,
                    systemPrompt = systemPrompt,
                    audioDurationSeconds = audioDurationSeconds,
                    transcriptionTokens = transcriptionTokens,
                    postProcessingTokens = null,
                    processingTimeMs = totalProcessingTimeMs,
                    transcriptionTimeMs = transcriptionTimeMs,
                    postProcessingTimeMs = postProcessTimeMs,
                    audioFileSizeBytes = audioFileSizeBytes
                )
                return ApiResult.Success(rewritten.ifBlank { transcribedText }, info)
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
            val llmApiService = llmServiceFactory.create(llmConfig)
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
                        translationTarget = if (apiSettings.outputLanguage.isNotEmpty()) LanguageNames.displayNameFor(apiSettings.outputLanguage) else null,
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

                    // Two-step path: only the post-processing leg's output is the
                    // user-visible final text, so chars/bytes get counted there.
                    // Transcription leg records audio duration + tokens only.
                    transcriptionTokens?.let { tokens ->
                        settingsRepository.recordUsage(
                            modelId = transcriptionModel,
                            inputTokens = tokens.promptTokens ?: 0,
                            outputTokens = tokens.completionTokens ?: 0,
                            totalTokens = tokens.totalTokens ?: 0,
                            audioDurationSeconds = audioDurationSeconds
                        )
                    }

                    val finalChars = processedText.length.toLong()
                    val finalBytes = processedText.toByteArray(Charsets.UTF_8).size.toLong()
                    postProcessingTokens?.let { tokens ->
                        settingsRepository.recordUsage(
                            modelId = postProcessModel,
                            inputTokens = tokens.promptTokens ?: 0,
                            outputTokens = tokens.completionTokens ?: 0,
                            totalTokens = tokens.totalTokens ?: 0,
                            audioDurationSeconds = 0.0, // Don't double-count audio duration
                            outputCharacters = finalChars,
                            outputBytes = finalBytes
                        )
                    } ?: settingsRepository.recordUsage(
                        // No token info from LLM provider; still record the chars.
                        modelId = postProcessModel,
                        inputTokens = 0,
                        outputTokens = 0,
                        totalTokens = 0,
                        audioDurationSeconds = 0.0,
                        outputCharacters = finalChars,
                        outputBytes = finalBytes
                    )

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
