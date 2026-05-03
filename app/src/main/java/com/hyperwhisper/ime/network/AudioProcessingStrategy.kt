package com.hyperwhisper.network

import android.util.Log
import com.hyperwhisper.data.*
import com.hyperwhisper.utils.TraceLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * Strategy Pattern for Audio Processing
 */
interface AudioProcessingStrategy {
    suspend fun processAudio(
        audioFile: File,
        audioBase64: String,
        voiceMode: VoiceMode,
        modelId: String
    ): ApiResult<String>
}

/**
 * Strategy A: Transcription (Whisper-style)
 * Used for verbatim transcription without transformations
 */
class TranscriptionStrategy(
    private val apiService: TranscriptionApiService,
    private val settingsRepository: com.hyperwhisper.data.SettingsRepository
) : AudioProcessingStrategy {

    companion object {
        private const val TAG = "TranscriptionStrategy"
    }

    override suspend fun processAudio(
        audioFile: File,
        audioBase64: String,
        voiceMode: VoiceMode,
        modelId: String
    ): ApiResult<String> {
        return try {
            Log.d(TAG, "========== TRANSCRIPTION REQUEST ==========")
            Log.d(TAG, "Processing audio with transcription strategy")

            // Get current API settings for language
            val apiSettings = settingsRepository.apiSettings.first()

            // Prepare multipart request
            val requestFile = audioFile.asRequestBody("audio/*".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData(
                "file",
                audioFile.name,
                requestFile
            )
            val modelPart = modelId.toRequestBody("text/plain".toMediaTypeOrNull())
            // Use verbose_json to get token usage and duration info
            val formatPart = "verbose_json".toRequestBody("text/plain".toMediaTypeOrNull())
            val timestampGranularityPart = "word".toRequestBody("text/plain".toMediaTypeOrNull())
            val languagePart = if (apiSettings.inputLanguage.isNotEmpty()) {
                apiSettings.inputLanguage.toRequestBody("text/plain".toMediaTypeOrNull())
            } else null

            // Log request details
            Log.d(TAG, "Request Details:")
            Log.d(TAG, "  Base URL: ${apiSettings.baseUrl}")
            val isLocalWhisper = apiSettings.provider == ApiProvider.SELFHOSTED_WHISPER
            val endpointPath = if (isLocalWhisper) "inference" else "audio/transcriptions"
            Log.d(TAG, "  Endpoint: $endpointPath")
            Log.d(TAG, "  Full URL: ${apiSettings.getCurrentBaseUrl()}$endpointPath")
            Log.d(TAG, "  Model: $modelId")
            Log.d(TAG, "  Language: ${if (apiSettings.inputLanguage.isEmpty()) "auto-detect" else apiSettings.inputLanguage}")
            Log.d(TAG, "  Audio file: ${audioFile.name} (${audioFile.length()} bytes)")
            Log.d(TAG, "  Audio format: ${audioFile.extension}")
            Log.d(TAG, "  Response format: verbose_json")
            Log.d(TAG, "  Timestamp granularity: word")
            Log.d(TAG, "  API Key: ${apiSettings.getCurrentApiKey().take(10)}...")

            // Make API call with additional parameters for token usage
            val response = if (isLocalWhisper) {
                apiService.transcribeLocal(
                    file = filePart,
                    responseFormat = formatPart,
                    language = languagePart
                )
            } else {
                apiService.transcribeWithDetails(
                    file = filePart,
                    model = modelPart,
                    responseFormat = formatPart,
                    timestampGranularity = timestampGranularityPart,
                    language = languagePart
                )
            }

            // Log response details
            Log.d(TAG, "Response Details:")
            Log.d(TAG, "  Status code: ${response.code()}")
            Log.d(TAG, "  Status message: ${response.message()}")
            Log.d(TAG, "  Headers: ${response.headers()}")

            if (response.isSuccessful) {
                val body = response.body()
                val transcription = body?.text ?: ""
                val tokenUsage = body?.usage
                // Calculate duration from file if API doesn't provide it
                // WAV format: 16kHz, mono, 16-bit = 32000 bytes/sec (minus 44 byte header)
                val estimatedDuration = if (audioFile.length() > 44) {
                    (audioFile.length() - 44) / 32000.0
                } else 0.0
                val duration = body?.duration ?: estimatedDuration

                Log.d(TAG, "✓ Transcription successful")
                Log.d(TAG, "  Result length: ${transcription.length} chars")
                Log.d(TAG, "  Duration: $duration seconds")
                tokenUsage?.let {
                    Log.d(TAG, "  Token usage: in=${it.promptTokens}, out=${it.completionTokens}, total=${it.totalTokens}")
                }
                Log.d(TAG, "  Result preview: ${transcription.take(100)}...")
                Log.d(TAG, "========== END REQUEST ==========")

                // Create ProcessingInfo with token usage
                val processingInfo = ProcessingInfo(
                    processingMode = "single-step",
                    strategy = "transcription",
                    transcriptionModel = modelId,
                    postProcessingModel = null,
                    translationEnabled = false,
                    translationTarget = null,
                    originalTranscription = null,
                    voiceModeName = voiceMode.name,
                    systemPrompt = voiceMode.systemPrompt,
                    audioDurationSeconds = duration,
                    transcriptionTokens = tokenUsage,
                    postProcessingTokens = null
                )

                ApiResult.Success(transcription, processingInfo)
            } else {
                val errorBody = response.errorBody()?.string() ?: "No error details"
                val statusCode = response.code()
                Log.e(TAG, "✗ Transcription failed")
                Log.e(TAG, "  Status code: $statusCode")
                Log.e(TAG, "  Error body: $errorBody")
                Log.d(TAG, "========== END REQUEST ==========")

                // Create detailed error message
                val errorMessage = buildString {
                    appendLine("API Request Failed")
                    appendLine()
                    appendLine("Provider: ${apiSettings.provider.displayName}")
                    appendLine("Model: $modelId")
                    appendLine("Endpoint: ${apiSettings.getCurrentBaseUrl()}$endpointPath")
                    appendLine()
                    appendLine("Status: $statusCode ${response.message()}")
                    appendLine()
                    when (statusCode) {
                        400 -> appendLine(if (isLocalWhisper) "Bad Request - Check audio format; local whisper.cpp expects a valid audio file" else "Bad Request - Check audio format or parameters")
                        401 -> appendLine("Authentication Failed - Check API key")
                        403 -> appendLine("Access Forbidden - Verify API key permissions")
                        404 -> appendLine("Endpoint Not Found - Check base URL")
                        429 -> appendLine("Rate Limit Exceeded - Wait before retrying")
                        500 -> appendLine("Server Error - Provider issue, try again later")
                        else -> appendLine("Error Details:")
                    }
                    appendLine()
                    append(errorBody.take(200))
                }

                ApiResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            val apiSettings = settingsRepository.apiSettings.first()
            TraceLogger.error(TAG, "Exception during transcription (provider=${apiSettings.provider.name}, model=$modelId)", e)
            Log.d(TAG, "========== END REQUEST ==========")

            // Create detailed error message
            val errorMessage = buildString {
                appendLine("Network/Processing Error")
                appendLine()
                appendLine("Provider: ${apiSettings.provider.displayName}")
                appendLine("Model: $modelId")
                appendLine("Endpoint: ${apiSettings.baseUrl}audio/transcriptions")
                appendLine()
                appendLine("Error Type: ${e.javaClass.simpleName}")
                appendLine()
                when {
                    e.message?.contains("Unable to resolve host") == true -> {
                        appendLine("Cannot reach server - Check internet connection")
                        appendLine("URL: ${apiSettings.baseUrl}")
                    }
                    e.message?.contains("timeout") == true -> {
                        appendLine("Request timed out - Server not responding")
                    }
                    e.message?.contains("SSL") == true || e.message?.contains("certificate") == true -> {
                        appendLine("SSL/Certificate error - Check HTTPS configuration")
                    }
                    e.message?.contains("JSON") == true || e.message?.contains("Expected") == true -> {
                        appendLine("Response parsing error - Invalid API response format")
                        appendLine("Expected JSON but got something else")
                    }
                    else -> {
                        appendLine("Details: ${e.message ?: "Unknown error"}")
                    }
                }
            }

            ApiResult.Error(errorMessage, e)
        }
    }
}

/**
 * Strategy B: Chat Completion with Audio
 * Used for transformations (polite, casual, translation, etc.)
 */
class ChatCompletionStrategy(
    val chatCompletionApiService: ChatCompletionApiService,
    private val settingsRepository: com.hyperwhisper.data.SettingsRepository
) : AudioProcessingStrategy {

    companion object {
        private const val TAG = "ChatCompletionStrategy"
    }

    /**
     * Build system prompt with optional translation instruction
     */
    private fun buildSystemPromptWithTranslation(basePrompt: String, outputLanguage: String): String {
        return if (outputLanguage.isNotEmpty()) {
            val language = SUPPORTED_LANGUAGES.find { it.code == outputLanguage }
            val languageName = language?.name ?: outputLanguage.uppercase()
            "$basePrompt\n\nIMPORTANT: Translate the output to $languageName. Return ONLY the $languageName translation, do not include the original text."
        } else {
            basePrompt
        }
    }

    override suspend fun processAudio(
        audioFile: File,
        audioBase64: String,
        voiceMode: VoiceMode,
        modelId: String
    ): ApiResult<String> {
        return try {
            Log.d(TAG, "========== CHAT COMPLETION REQUEST ==========")
            Log.d(TAG, "Processing audio with chat completion strategy")

            // Get current API settings
            val apiSettings = settingsRepository.apiSettings.first()

            // Build system prompt with translation if needed
            val systemPrompt = buildSystemPromptWithTranslation(
                voiceMode.systemPrompt,
                apiSettings.outputLanguage
            )

            // Determine audio format
            val audioFormat = when (audioFile.extension.lowercase()) {
                "m4a" -> "mp4"
                "wav" -> "wav"
                "mp3" -> "mp3"
                else -> "mp4"
            }

            // Build chat completion request
            val request = ChatCompletionRequest(
                model = modelId,
                messages = listOf(
                    ChatMessage(
                        role = "user",
                        content = listOf(
                            ContentPart.TextContent(text = systemPrompt),
                            ContentPart.AudioContent(
                                inputAudio = InputAudio(
                                    data = audioBase64,
                                    format = audioFormat
                                )
                            )
                        )
                    )
                )
            )

            // Log request details
            Log.d(TAG, "Request Details:")
            Log.d(TAG, "  Base URL: ${apiSettings.baseUrl}")
            Log.d(TAG, "  Endpoint: chat/completions")
            Log.d(TAG, "  Full URL: ${apiSettings.baseUrl}chat/completions")
            Log.d(TAG, "  Model: $modelId")
            Log.d(TAG, "  Voice Mode: ${voiceMode.name}")
            Log.d(TAG, "  System Prompt: ${systemPrompt.take(100)}...")
            if (apiSettings.outputLanguage.isNotEmpty()) {
                val language = SUPPORTED_LANGUAGES.find { it.code == apiSettings.outputLanguage }
                Log.d(TAG, "  Translation enabled: ${language?.name ?: apiSettings.outputLanguage}")
            }
            Log.d(TAG, "  Audio file: ${audioFile.name} (${audioFile.length()} bytes)")
            Log.d(TAG, "  Audio format: $audioFormat")
            Log.d(TAG, "  Audio base64 length: ${audioBase64.length} chars")
            Log.d(TAG, "  API Key: ${apiSettings.getCurrentApiKey().take(10)}...")

            // Make API call
            val response = chatCompletionApiService.chatCompletion(request)

            // Log response details
            Log.d(TAG, "Response Details:")
            Log.d(TAG, "  Status code: ${response.code()}")
            Log.d(TAG, "  Status message: ${response.message()}")
            Log.d(TAG, "  Headers: ${response.headers()}")

            if (response.isSuccessful) {
                val responseBody = response.body()
                val result = responseBody?.choices?.firstOrNull()?.message?.content ?: ""
                val tokenUsage = responseBody?.usage

                Log.d(TAG, "✓ Chat completion successful")
                Log.d(TAG, "  Result length: ${result.length} chars")
                Log.d(TAG, "  Result preview: ${result.take(100)}...")
                tokenUsage?.let {
                    Log.d(TAG, "  Token usage: in=${it.promptTokens}, out=${it.completionTokens}, total=${it.totalTokens}")
                }
                Log.d(TAG, "========== END REQUEST ==========")

                // Create ProcessingInfo with token usage
                val processingInfo = tokenUsage?.let {
                    ProcessingInfo(
                        processingMode = "single-step",
                        strategy = "chat-completion",
                        transcriptionModel = modelId,
                        postProcessingModel = null,
                        translationEnabled = false,
                        translationTarget = null,
                        originalTranscription = null,
                        voiceModeName = voiceMode.name,
                        systemPrompt = "",
                        audioDurationSeconds = 0.0,
                        transcriptionTokens = it,
                        postProcessingTokens = null
                    )
                }

                ApiResult.Success(result, processingInfo)
            } else {
                val errorBody = response.errorBody()?.string() ?: "No error details"
                val statusCode = response.code()
                Log.e(TAG, "✗ Chat completion failed")
                Log.e(TAG, "  Status code: $statusCode")
                Log.e(TAG, "  Error body: $errorBody")
                Log.d(TAG, "========== END REQUEST ==========")

                // Create detailed error message
                val errorMessage = buildString {
                    appendLine("API Request Failed")
                    appendLine()
                    appendLine("Provider: ${apiSettings.provider.displayName}")
                    appendLine("Model: $modelId")
                    appendLine("Voice Mode: ${voiceMode.name}")
                    appendLine("Endpoint: ${apiSettings.baseUrl}chat/completions")
                    appendLine()
                    appendLine("Status: $statusCode ${response.message()}")
                    appendLine()
                    when (statusCode) {
                        400 -> appendLine("Bad Request - Check audio format or model compatibility")
                        401 -> appendLine("Authentication Failed - Check API key")
                        403 -> appendLine("Access Forbidden - Verify API key permissions")
                        404 -> appendLine("Endpoint Not Found - Check base URL")
                        429 -> appendLine("Rate Limit Exceeded - Wait before retrying")
                        500 -> appendLine("Server Error - Provider issue, try again later")
                        else -> appendLine("Error Details:")
                    }
                    appendLine()
                    append(errorBody.take(200))
                }

                ApiResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            val apiSettings = settingsRepository.apiSettings.first()
            TraceLogger.error(TAG, "Exception during chat completion (provider=${apiSettings.provider.name}, model=$modelId, mode=${voiceMode.name})", e)
            Log.d(TAG, "========== END REQUEST ==========")

            // Create detailed error message
            val errorMessage = buildString {
                appendLine("Network/Processing Error")
                appendLine()
                appendLine("Provider: ${apiSettings.provider.displayName}")
                appendLine("Model: $modelId")
                appendLine("Voice Mode: ${voiceMode.name}")
                appendLine("Endpoint: ${apiSettings.baseUrl}chat/completions")
                appendLine()
                appendLine("Error Type: ${e.javaClass.simpleName}")
                appendLine()
                when {
                    e.message?.contains("Unable to resolve host") == true -> {
                        appendLine("Cannot reach server - Check internet connection")
                        appendLine("URL: ${apiSettings.baseUrl}")
                    }
                    e.message?.contains("timeout") == true -> {
                        appendLine("Request timed out - Server not responding")
                    }
                    e.message?.contains("SSL") == true || e.message?.contains("certificate") == true -> {
                        appendLine("SSL/Certificate error - Check HTTPS configuration")
                    }
                    e.message?.contains("JSON") == true || e.message?.contains("Expected") == true -> {
                        appendLine("Response parsing error - Invalid API response format")
                        appendLine("Expected JSON but got something else")
                    }
                    else -> {
                        appendLine("Details: ${e.message ?: "Unknown error"}")
                    }
                }
            }

            ApiResult.Error(errorMessage, e)
        }
    }
}

/**
 * Strategy C: Local Processing (On-device)
 *
 * On-device transcription via whisper.cpp (JNI). Decode the recorder's M4A/AAC
 * (or any container MediaCodec accepts) into 16 kHz mono float32 PCM, push it
 * through a cached [WhisperContext], optionally pass through Gemma for
 * voice-mode-aware post-processing (polite/casual/translation/etc.) via the
 * [GemmaInferenceEngine] when [LocalModelSettings.useLocalGemma] is on.
 */
class LocalProcessingStrategy(
    private val settingsRepository: com.hyperwhisper.data.SettingsRepository,
    private val whisperCache: com.hyperwhisper.ime.whisper.WhisperContextCache,
    private val gemma: com.hyperwhisper.ime.llm.GemmaInferenceEngine
) : AudioProcessingStrategy {

    companion object {
        private const val TAG = "LocalProcessingStrategy"
    }

    override suspend fun processAudio(
        audioFile: File,
        audioBase64: String,
        voiceMode: VoiceMode,
        modelId: String
    ): ApiResult<String> {
        return try {
            Log.d(TAG, "========== LOCAL PROCESSING REQUEST ==========")
            val settings = settingsRepository.apiSettings.first()
            val localSettings = settings.localModelSettings

            if (localSettings.whisperModelPath.isEmpty()) {
                return ApiResult.Error("Local Whisper model path is not configured.")
            }
            val whisperModel = File(localSettings.whisperModelPath)
            if (!whisperModel.exists()) {
                return ApiResult.Error(
                    "Local Whisper model not found at: ${localSettings.whisperModelPath}"
                )
            }

            Log.d(TAG, "Local transcription: model=${whisperModel.name}, audio=${audioFile.name} (${audioFile.length()} B)")
            val startTime = System.currentTimeMillis()

            val ctx = whisperCache.get(localSettings.whisperModelPath)

            val decodeStart = System.currentTimeMillis()
            val samples = withContext(Dispatchers.Default) {
                com.hyperwhisper.ime.audio.AudioDecoder.decodeTo16kMonoFloat(audioFile)
            }
            val decodeMs = System.currentTimeMillis() - decodeStart
            val audioSeconds = samples.size.toDouble() /
                com.hyperwhisper.ime.audio.AudioDecoder.WHISPER_SAMPLE_RATE
            Log.d(TAG, "Decoded ${samples.size} samples (${"%.2f".format(audioSeconds)} s) in ${decodeMs} ms")

            val inferenceStart = System.currentTimeMillis()
            val language = settings.inputLanguage.takeIf { it.isNotBlank() }
            val translate = settings.outputLanguage.equals("en", ignoreCase = true) &&
                language != null && !language.equals("en", ignoreCase = true)
            val rawText = ctx.transcribe(
                samples = samples,
                language = language,
                translate = translate
            ).trim()
            val transcriptionTime = System.currentTimeMillis() - inferenceStart
            Log.d(TAG, "whisper_full done in ${transcriptionTime} ms; len=${rawText.length}")

            if (rawText.isEmpty()) {
                return ApiResult.Error(
                    "On-device Whisper returned no text. Try a different model or louder audio."
                )
            }

            // Local LLM post-processing — Gemma rewrites the raw transcription
            // according to the voice mode's system prompt (polite/casual/etc.)
            // when the user has set a Gemma model and switched on the toggle.
            // For "verbatim" / "direct" modes we skip; the raw text is the goal.
            val skipLocalLlm = voiceMode.processingMode == "direct" ||
                voiceMode.id.equals("verbatim", ignoreCase = true) ||
                voiceMode.systemPrompt.isBlank()
            val canRunLocalLlm = localSettings.useLocalGemma &&
                localSettings.gemmaModelPath.isNotBlank() &&
                File(localSettings.gemmaModelPath).exists()

            var finalResult = rawText
            var postProcessingTimeMs: Long? = null
            var postProcessingModelName: String? = null

            if (!skipLocalLlm && canRunLocalLlm) {
                Log.d(TAG, "Local LLM post-processing with ${File(localSettings.gemmaModelPath).name}")
                val ppStart = System.currentTimeMillis()
                try {
                    val rewritten = gemma.rewrite(
                        modelPath = localSettings.gemmaModelPath,
                        systemPrompt = voiceMode.systemPrompt,
                        userText = rawText
                    )
                    if (rewritten.isNotBlank()) finalResult = rewritten
                    postProcessingTimeMs = System.currentTimeMillis() - ppStart
                    postProcessingModelName = File(localSettings.gemmaModelPath).name
                    Log.d(TAG, "Gemma post-processing done in ${postProcessingTimeMs} ms")
                } catch (t: Throwable) {
                    // Don't fail the whole transcription if Gemma blows up —
                    // the raw Whisper text is still useful.
                    TraceLogger.error(TAG, "Gemma post-processing failed; returning raw transcription", t)
                }
            } else if (canRunLocalLlm) {
                Log.d(TAG, "Skipping Gemma — voice mode is verbatim/direct")
            }

            val totalTime = System.currentTimeMillis() - startTime

            val processingInfo = ProcessingInfo(
                processingMode = if (postProcessingModelName != null) "two-step" else "single-step",
                strategy = "local",
                transcriptionModel = whisperModel.name,
                postProcessingModel = postProcessingModelName,
                voiceModeName = voiceMode.name,
                systemPrompt = voiceMode.systemPrompt,
                audioDurationSeconds = audioSeconds,
                processingTimeMs = totalTime,
                transcriptionTimeMs = transcriptionTime,
                postProcessingTimeMs = postProcessingTimeMs,
                audioFileSizeBytes = audioFile.length()
            )

            Log.d(TAG, "✓ Local processing complete in ${totalTime}ms (decode=${decodeMs}, whisper=${transcriptionTime})")
            ApiResult.Success(finalResult, processingInfo)

        } catch (e: Exception) {
            TraceLogger.error(TAG, "Error in local processing", e)
            ApiResult.Error(
                "Local processing failed: ${e.javaClass.simpleName}: ${e.message ?: "no message"}",
                e
            )
        } catch (t: Throwable) {
            // Local inference can OOM (large models) or hit UnsatisfiedLinkError
            // (missing native lib). Convert to a graceful ApiResult.Error so the
            // IME doesn't crash on misconfigured local models.
            TraceLogger.error(TAG, "Fatal error in local inference — converted to ApiResult.Error", t)
            ApiResult.Error(
                "Local inference failed: ${t.javaClass.simpleName}: ${t.message ?: "no message"}"
            )
        }
    }
}
