package com.hyperwhisper.data

import com.google.gson.annotations.SerializedName

/**
 * API Request/Response DTOs
 */

// Strategy A: Transcription (Whisper-style)
data class TranscriptionRequest(
    @SerializedName("file") val file: String,
    @SerializedName("model") val model: String,
    @SerializedName("response_format") val responseFormat: String = "text"
)

data class TranscriptionResponse(
    @SerializedName("text") val text: String,
    @SerializedName("duration") val duration: Double? = null,
    @SerializedName("words") val words: List<WordInfo>? = null,
    @SerializedName("usage") val usage: TokenUsage? = null
)

data class WordInfo(
    @SerializedName("word") val word: String,
    @SerializedName("start") val start: Double,
    @SerializedName("end") val end: Double
)

// Strategy B: Chat Completion with Audio
data class ChatCompletionRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<ChatMessage>,
    @SerializedName("modalities") val modalities: List<String> = listOf("text", "audio")
)

data class ChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: List<ContentPart>
)

sealed class ContentPart {
    data class TextContent(
        @SerializedName("type") val type: String = "text",
        @SerializedName("text") val text: String
    ) : ContentPart()

    data class AudioContent(
        @SerializedName("type") val type: String = "input_audio",
        @SerializedName("input_audio") val inputAudio: InputAudio
    ) : ContentPart()
}

data class InputAudio(
    @SerializedName("data") val data: String, // Base64
    @SerializedName("format") val format: String = "wav"
)

data class ChatCompletionResponse(
    @SerializedName("id") val id: String,
    @SerializedName("choices") val choices: List<Choice>,
    @SerializedName("usage") val usage: TokenUsage? = null
)

data class Choice(
    @SerializedName("message") val message: ResponseMessage,
    @SerializedName("finish_reason") val finishReason: String
)

data class ResponseMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

/**
 * Processing information for transparency
 */
data class ProcessingInfo(
    val processingMode: String, // "single-step" or "two-step"
    val strategy: String, // "transcription" or "chat-completion"
    val transcriptionModel: String, // Model used for transcription
    val postProcessingModel: String? = null, // Model used for post-processing (null if single-step)
    val translationEnabled: Boolean = false, // Whether translation was applied
    val translationTarget: String? = null, // Target language for translation
    val originalTranscription: String? = null, // Original text before post-processing (null if single-step)
    val voiceModeName: String, // Name of voice mode used
    val systemPrompt: String, // System prompt that was used
    val audioDurationSeconds: Double = 0.0, // Audio duration in seconds
    val transcriptionTokens: TokenUsage? = null, // Tokens used for transcription
    val postProcessingTokens: TokenUsage? = null, // Tokens used for post-processing (if applicable)
    val processingTimeMs: Long = 0L, // Total processing time in milliseconds
    val transcriptionTimeMs: Long? = null, // Time for transcription step in milliseconds
    val postProcessingTimeMs: Long? = null, // Time for post-processing step in milliseconds
    val audioFileSizeBytes: Long = 0L, // Audio file size in bytes
    val timestamp: Long = System.currentTimeMillis() // When processing started
)
