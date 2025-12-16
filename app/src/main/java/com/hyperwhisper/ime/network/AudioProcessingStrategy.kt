package com.hyperwhisper.network

import android.util.Log
import com.hyperwhisper.data.*
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
    private val apiService: TranscriptionApiService
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
            Log.d(TAG, "Processing audio with transcription strategy")

            // Prepare multipart request
            val requestFile = audioFile.asRequestBody("audio/*".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData(
                "file",
                audioFile.name,
                requestFile
            )
            val modelPart = modelId.toRequestBody("text/plain".toMediaTypeOrNull())
            val formatPart = "text".toRequestBody("text/plain".toMediaTypeOrNull())

            // Make API call
            val response = apiService.transcribe(
                file = filePart,
                model = modelPart,
                responseFormat = formatPart
            )

            if (response.isSuccessful) {
                val transcription = response.body()?.text ?: ""
                Log.d(TAG, "Transcription successful: $transcription")
                ApiResult.Success(transcription)
            } else {
                val error = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "Transcription failed: $error")
                ApiResult.Error("API Error: ${response.code()} - $error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during transcription", e)
            ApiResult.Error("Network error: ${e.message}", e)
        }
    }
}

/**
 * Strategy B: Chat Completion with Audio
 * Used for transformations (polite, casual, translation, etc.)
 */
class ChatCompletionStrategy(
    private val apiService: ChatCompletionApiService
) : AudioProcessingStrategy {

    companion object {
        private const val TAG = "ChatCompletionStrategy"
    }

    override suspend fun processAudio(
        audioFile: File,
        audioBase64: String,
        voiceMode: VoiceMode,
        modelId: String
    ): ApiResult<String> {
        return try {
            Log.d(TAG, "Processing audio with chat completion strategy")

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
                            ContentPart.TextContent(text = voiceMode.systemPrompt),
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

            // Make API call
            val response = apiService.chatCompletion(request)

            if (response.isSuccessful) {
                val result = response.body()?.choices?.firstOrNull()?.message?.content ?: ""
                Log.d(TAG, "Chat completion successful: $result")
                ApiResult.Success(result)
            } else {
                val error = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "Chat completion failed: $error")
                ApiResult.Error("API Error: ${response.code()} - $error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during chat completion", e)
            ApiResult.Error("Network error: ${e.message}", e)
        }
    }
}
