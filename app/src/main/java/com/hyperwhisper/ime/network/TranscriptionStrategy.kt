package com.hyperwhisper.network

import android.util.Log
import com.hyperwhisper.data.*
import com.hyperwhisper.utils.TraceLogger
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

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
