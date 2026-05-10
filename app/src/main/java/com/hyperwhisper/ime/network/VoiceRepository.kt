package com.hyperwhisper.network

import android.util.Log
import com.hyperwhisper.audio.AudioRecorderManager
import com.hyperwhisper.data.*
import com.hyperwhisper.data.telemetry.ColdStartTracker
import com.hyperwhisper.data.telemetry.DeviceSnapshotProvider
import com.hyperwhisper.data.telemetry.PerformanceRepository
import com.hyperwhisper.data.telemetry.SessionTimer
import com.hyperwhisper.data.telemetry.SessionType
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceRepository @Inject constructor(
    private val audioRecorderManager: AudioRecorderManager,
    private val transcriptionStrategy: TranscriptionStrategy,
    private val chatCompletionStrategy: ChatCompletionStrategy,
    private val settingsRepository: SettingsRepository,
    private val performanceRepository: PerformanceRepository,
    private val coldStartTracker: ColdStartTracker,
    private val deviceSnapshotProvider: DeviceSnapshotProvider
) {
    companion object {
        private const val TAG = "VoiceRepository"
        private const val POST_PROCESS_MODEL = "gpt-4o-mini"
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
        val device = deviceSnapshotProvider.snapshot()
        val coldStartKind = coldStartTracker.classify(apiSettings.modelId)
        val timer = SessionTimer.start(
            sessionType = SessionType.CLOUD,
            provider = apiSettings.provider.name,
            modelId = apiSettings.modelId,
            coldStartKind = coldStartKind,
            device = device,
            inputLanguage = apiSettings.inputLanguage.ifEmpty { null }
        )

        var audioDurationSeconds = 0.0

        return try {
            Log.d(TAG, "Processing audio with mode: ${voiceMode.name}, provider: ${apiSettings.provider}")

            timer.mark("audio_duration_calc")
            audioDurationSeconds = calculateAudioDuration(audioFile)
            Log.d(TAG, "Audio duration: $audioDurationSeconds seconds")

            timer.mark("base64_encode")
            val base64Result = audioRecorderManager.audioFileToBase64(audioFile)
            if (base64Result.isFailure) {
                commitFailure(timer, audioDurationSeconds, "base64_encode_failed")
                return ApiResult.Error("Failed to encode audio: ${base64Result.exceptionOrNull()?.message}")
            }
            val audioBase64 = base64Result.getOrNull() ?: ""

            val needsTwoStepProcessing = needsTwoStepProcessing(voiceMode, apiSettings)

            if (needsTwoStepProcessing) {
                Log.d(TAG, "Using two-step processing: transcribe + post-process")
                val transcriptionResult = transcriptionStrategy.processAudio(
                    audioFile = audioFile,
                    audioBase64 = audioBase64,
                    voiceMode = voiceMode.copy(systemPrompt = "Transcribe the audio exactly as spoken."),
                    modelId = apiSettings.modelId,
                    timer = timer
                )

                when (transcriptionResult) {
                    is ApiResult.Success -> {
                        Log.d(TAG, "Transcription successful, applying post-processing")
                        val tx = transcriptionResult.processingInfo?.transcriptionTokens
                        commitSuccess(
                            timer,
                            audioDurationSeconds,
                            transcriptionResult.data.length,
                            tx?.promptTokens, tx?.completionTokens, tx?.totalTokens
                        )
                        return postProcessText(
                            transcribedText = transcriptionResult.data,
                            voiceMode = voiceMode,
                            apiSettings = apiSettings,
                            transcriptionModel = apiSettings.modelId,
                            audioDurationSeconds = audioDurationSeconds,
                            transcriptionTokens = transcriptionResult.processingInfo?.transcriptionTokens,
                            previousSessionId = timer.sessionId,
                            device = device
                        )
                    }
                    is ApiResult.Error -> {
                        commitFailure(timer, audioDurationSeconds, "transcription_api_error")
                        return transcriptionResult
                    }
                    else -> {
                        commitFailure(timer, audioDurationSeconds, "transcription_unexpected_result")
                        return ApiResult.Error("Unexpected result from transcription")
                    }
                }
            } else {
                val strategy = selectStrategy(voiceMode, apiSettings.provider)
                val strategyName = if (strategy is TranscriptionStrategy) "transcription" else "chat-completion"
                val systemPrompt = buildSystemPrompt(voiceMode.systemPrompt, apiSettings.outputLanguage)

                val result = strategy.processAudio(
                    audioFile = audioFile,
                    audioBase64 = audioBase64,
                    voiceMode = voiceMode,
                    modelId = apiSettings.modelId,
                    timer = timer
                )

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

                        result.processingInfo?.transcriptionTokens?.let { tokens ->
                            settingsRepository.recordUsage(
                                modelId = apiSettings.modelId,
                                inputTokens = tokens.promptTokens ?: 0,
                                outputTokens = tokens.completionTokens ?: 0,
                                totalTokens = tokens.totalTokens ?: 0,
                                audioDurationSeconds = audioDurationSeconds
                            )
                        }

                        val tokens = result.processingInfo?.transcriptionTokens
                        commitSuccess(
                            timer,
                            audioDurationSeconds,
                            result.data.length,
                            tokens?.promptTokens, tokens?.completionTokens, tokens?.totalTokens
                        )

                        ApiResult.Success(result.data, processingInfo)
                    }
                    is ApiResult.Error -> {
                        commitFailure(timer, audioDurationSeconds, "api_error")
                        result
                    }
                    else -> {
                        commitFailure(timer, audioDurationSeconds, "unknown_result")
                        result
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio", e)
            commitFailure(
                timer,
                audioDurationSeconds,
                e.javaClass.simpleName ?: "unknown_exception"
            )
            ApiResult.Error("Processing failed: ${e.message}", e)
        }
    }

    private suspend fun commitSuccess(
        timer: SessionTimer,
        audioDurationSeconds: Double,
        outputChars: Int,
        inputTokens: Int?,
        outputTokens: Int?,
        totalTokens: Int?,
        retryOf: String? = null
    ) {
        try {
            timer.commit(
                repo = performanceRepository,
                audioDurationMs = (audioDurationSeconds * 1000).toLong(),
                outputChars = outputChars,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                totalTokens = totalTokens,
                detectedLanguage = null,
                success = true,
                errorKind = null,
                retryOf = retryOf
            )
        } catch (t: Throwable) {
            Log.e(TAG, "telemetry commitSuccess failed", t)
        }
    }

    private suspend fun commitFailure(
        timer: SessionTimer,
        audioDurationSeconds: Double,
        errorKind: String,
        retryOf: String? = null
    ) {
        try {
            timer.commit(
                repo = performanceRepository,
                audioDurationMs = (audioDurationSeconds * 1000).toLong(),
                outputChars = 0,
                inputTokens = null,
                outputTokens = null,
                totalTokens = null,
                detectedLanguage = null,
                success = false,
                errorKind = errorKind,
                retryOf = retryOf
            )
        } catch (t: Throwable) {
            Log.e(TAG, "telemetry commitFailure failed", t)
        }
    }

    /**
     * Determine if two-step processing is needed
     */
    private fun needsTwoStepProcessing(
        voiceMode: VoiceMode,
        apiSettings: ApiSettings
    ): Boolean {
        val needsTranslation = apiSettings.outputLanguage.isNotEmpty() &&
            apiSettings.outputLanguage != apiSettings.inputLanguage

        if (apiSettings.provider == ApiProvider.OPENROUTER) return false
        if (apiSettings.provider == ApiProvider.GEMINI) return false
        if (apiSettings.provider == ApiProvider.HUGGINGFACE) return true
        if (voiceMode.id == "verbatim") return needsTranslation
        return true
    }

    private fun getLanguageName(languageCode: String): String {
        val language = SUPPORTED_LANGUAGES.find { it.code == languageCode }
        return language?.name ?: languageCode.uppercase()
    }

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
     */
    private suspend fun postProcessText(
        transcribedText: String,
        voiceMode: VoiceMode,
        apiSettings: ApiSettings,
        transcriptionModel: String,
        audioDurationSeconds: Double,
        transcriptionTokens: TokenUsage?,
        previousSessionId: String?,
        device: com.hyperwhisper.data.telemetry.DeviceSnapshot
    ): ApiResult<String> {
        val coldStartKind = coldStartTracker.classify(POST_PROCESS_MODEL)
        val ppTimer = SessionTimer.start(
            sessionType = SessionType.CLOUD,
            provider = apiSettings.provider.name,
            modelId = POST_PROCESS_MODEL,
            coldStartKind = coldStartKind,
            device = device,
            inputLanguage = apiSettings.inputLanguage.ifEmpty { null }
        )

        return try {
            val systemPrompt = buildSystemPrompt(voiceMode.systemPrompt, apiSettings.outputLanguage)
            Log.d(TAG, "Post-processing text with system prompt: $systemPrompt")

            val postProcessProvider = apiSettings.provider
            Log.d(TAG, "Using provider for post-processing: ${postProcessProvider.displayName}, model: $POST_PROCESS_MODEL")
            if (apiSettings.outputLanguage.isNotEmpty()) {
                Log.d(TAG, "Translation enabled: output language = ${getLanguageName(apiSettings.outputLanguage)}")
            }

            ppTimer.mark("request_build")
            val request = ChatCompletionRequest(
                model = POST_PROCESS_MODEL,
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = listOf(ContentPart.TextContent(text = systemPrompt))
                    ),
                    ChatMessage(
                        role = "user",
                        content = listOf(ContentPart.TextContent(text = transcribedText))
                    )
                ),
                modalities = listOf("text")
            )

            ppTimer.mark("network")
            val response = chatCompletionStrategy.chatCompletionApiService.chatCompletion(request)
            ppTimer.mark("response_parse")

            if (response.isSuccessful) {
                val result = response.body()
                val processedText = result?.choices?.firstOrNull()?.message?.content
                val postProcessingTokens = result?.usage

                if (processedText != null) {
                    Log.d(TAG, "Post-processing successful")

                    val processingInfo = ProcessingInfo(
                        processingMode = "two-step",
                        strategy = "transcription + chat-completion",
                        transcriptionModel = transcriptionModel,
                        postProcessingModel = POST_PROCESS_MODEL,
                        translationEnabled = apiSettings.outputLanguage.isNotEmpty(),
                        translationTarget = if (apiSettings.outputLanguage.isNotEmpty()) getLanguageName(apiSettings.outputLanguage) else null,
                        originalTranscription = transcribedText,
                        voiceModeName = voiceMode.name,
                        systemPrompt = systemPrompt,
                        audioDurationSeconds = audioDurationSeconds,
                        transcriptionTokens = transcriptionTokens,
                        postProcessingTokens = postProcessingTokens
                    )

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
                            modelId = POST_PROCESS_MODEL,
                            inputTokens = tokens.promptTokens ?: 0,
                            outputTokens = tokens.completionTokens ?: 0,
                            totalTokens = tokens.totalTokens ?: 0,
                            audioDurationSeconds = 0.0
                        )
                    }

                    commitSuccess(
                        ppTimer,
                        audioDurationSeconds = 0.0,
                        outputChars = processedText.length,
                        inputTokens = postProcessingTokens?.promptTokens,
                        outputTokens = postProcessingTokens?.completionTokens,
                        totalTokens = postProcessingTokens?.totalTokens,
                        retryOf = previousSessionId
                    )

                    ApiResult.Success(processedText, processingInfo)
                } else {
                    Log.w(TAG, "No processed text in response, returning original")
                    commitFailure(ppTimer, 0.0, "empty_response", retryOf = previousSessionId)
                    ApiResult.Success(transcribedText)
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Post-processing API error: ${response.code()} - $errorBody")
                Log.w(TAG, "Post-processing failed, returning original transcription")
                commitFailure(ppTimer, 0.0, "http_${response.code()}", retryOf = previousSessionId)
                ApiResult.Success(transcribedText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in post-processing, returning original text", e)
            commitFailure(
                ppTimer,
                0.0,
                e.javaClass.simpleName ?: "unknown_exception",
                retryOf = previousSessionId
            )
            ApiResult.Success(transcribedText)
        }
    }

    private fun selectStrategy(
        voiceMode: VoiceMode,
        provider: ApiProvider
    ): AudioProcessingStrategy {
        return when {
            provider == ApiProvider.OPENROUTER -> {
                Log.d(TAG, "Selected ChatCompletionStrategy (OpenRouter)")
                chatCompletionStrategy
            }
            provider == ApiProvider.GEMINI -> {
                Log.d(TAG, "Selected ChatCompletionStrategy (Gemini)")
                chatCompletionStrategy
            }
            provider == ApiProvider.HUGGINGFACE -> {
                Log.d(TAG, "Selected ChatCompletionStrategy (HuggingFace - text-only)")
                chatCompletionStrategy
            }
            voiceMode.id == "verbatim" && (provider == ApiProvider.OPENAI || provider == ApiProvider.GROQ) -> {
                Log.d(TAG, "Selected TranscriptionStrategy (Verbatim)")
                transcriptionStrategy
            }
            else -> {
                Log.d(TAG, "Selected ChatCompletionStrategy (Transformation)")
                chatCompletionStrategy
            }
        }
    }

    suspend fun startRecording(): Result<Unit> {
        return audioRecorderManager.startRecording()
    }

    suspend fun stopRecording(): Result<File> {
        return audioRecorderManager.stopRecording()
    }

    suspend fun cancelRecording() {
        audioRecorderManager.cancelRecording()
    }

    fun isRecording(): Boolean {
        return audioRecorderManager.isCurrentlyRecording()
    }

    private fun calculateAudioDuration(audioFile: File): Double {
        return try {
            val fileSizeBytes = audioFile.length()
            fileSizeBytes / 16000.0
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating audio duration", e)
            0.0
        }
    }
}
