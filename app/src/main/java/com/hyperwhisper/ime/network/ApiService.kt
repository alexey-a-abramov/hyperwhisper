package com.hyperwhisper.network

import com.hyperwhisper.data.ChatCompletionRequest
import com.hyperwhisper.data.ChatCompletionResponse
import com.hyperwhisper.data.TranscriptionResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API Service for Transcription (Whisper-style)
 */
interface TranscriptionApiService {
    @Multipart
    @POST("audio/transcriptions")
    suspend fun transcribe(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("response_format") responseFormat: RequestBody? = null,
        @Part("language") language: RequestBody? = null
    ): Response<TranscriptionResponse>
}

/**
 * Retrofit API Service for Chat Completion with Audio
 */
interface ChatCompletionApiService {
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Body request: ChatCompletionRequest
    ): Response<ChatCompletionResponse>
}
