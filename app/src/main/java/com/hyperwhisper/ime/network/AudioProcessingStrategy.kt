package com.hyperwhisper.network

import android.util.Log
import com.hyperwhisper.data.*
import kotlinx.coroutines.flow.first
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
            val formatPart = "json".toRequestBody("text/plain".toMediaTypeOrNull())
            val languagePart = if (apiSettings.inputLanguage.isNotEmpty()) {
                apiSettings.inputLanguage.toRequestBody("text/plain".toMediaTypeOrNull())
            } else null

            // Log request details
            Log.d(TAG, "Request Details:")
            Log.d(TAG, "  Base URL: ${apiSettings.baseUrl}")
            Log.d(TAG, "  Endpoint: audio/transcriptions")
            Log.d(TAG, "  Full URL: ${apiSettings.baseUrl}audio/transcriptions")
            Log.d(TAG, "  Model: $modelId")
            Log.d(TAG, "  Language: ${if (apiSettings.inputLanguage.isEmpty()) "auto-detect" else apiSettings.inputLanguage}")
            Log.d(TAG, "  Audio file: ${audioFile.name} (${audioFile.length()} bytes)")
            Log.d(TAG, "  Audio format: ${audioFile.extension}")
            Log.d(TAG, "  Response format: json")
            Log.d(TAG, "  API Key: ${apiSettings.getCurrentApiKey().take(10)}...")

            // Make API call
            val response = apiService.transcribe(
                file = filePart,
                model = modelPart,
                responseFormat = formatPart,
                language = languagePart
            )

            // Log response details
            Log.d(TAG, "Response Details:")
            Log.d(TAG, "  Status code: ${response.code()}")
            Log.d(TAG, "  Status message: ${response.message()}")
            Log.d(TAG, "  Headers: ${response.headers()}")

            if (response.isSuccessful) {
                val transcription = response.body()?.text ?: ""
                Log.d(TAG, "✓ Transcription successful")
                Log.d(TAG, "  Result length: ${transcription.length} chars")
                Log.d(TAG, "  Result preview: ${transcription.take(100)}...")
                Log.d(TAG, "========== END REQUEST ==========")
                ApiResult.Success(transcription)
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
                    appendLine("Endpoint: ${apiSettings.baseUrl}audio/transcriptions")
                    appendLine()
                    appendLine("Status: $statusCode ${response.message()}")
                    appendLine()
                    when (statusCode) {
                        400 -> appendLine("Bad Request - Check audio format or parameters")
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
            Log.e(TAG, "✗ Exception during transcription", e)
            Log.e(TAG, "  Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "  Exception message: ${e.message}")
            Log.e(TAG, "  Stack trace: ${e.stackTraceToString()}")
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
            Log.e(TAG, "✗ Exception during chat completion", e)
            Log.e(TAG, "  Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "  Exception message: ${e.message}")
            Log.e(TAG, "  Stack trace: ${e.stackTraceToString()}")
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
