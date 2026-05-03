package com.hyperwhisper.data

import com.google.gson.annotations.SerializedName

/**
 * Transcription history item
 */
data class TranscriptionHistoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val audioFilePath: String? = null  // Path to saved audio file for reprocessing
)

/**
 * Usage statistics for a specific model
 */
data class ModelUsage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0
) {
    operator fun plus(other: ModelUsage): ModelUsage {
        return ModelUsage(
            inputTokens = this.inputTokens + other.inputTokens,
            outputTokens = this.outputTokens + other.outputTokens,
            totalTokens = this.totalTokens + other.totalTokens
        )
    }
}

/**
 * Overall usage statistics
 */
data class UsageStatistics(
    val modelUsage: Map<String, ModelUsage> = emptyMap(), // modelId -> usage
    val totalAudioSeconds: Double = 0.0,
    // Final user-visible output characters across all sessions. For two-step
    // (transcription + post-processing) flows, only the post-processing leg
    // counts so we don't double-count the intermediate transcription text.
    val totalCharacters: Long = 0L,
    // UTF-8 byte count of the same final output. Tracked separately from chars
    // because non-Latin scripts run ~2-3 bytes/char.
    val totalBytes: Long = 0L
)

/**
 * Token usage from API response
 */
data class TokenUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int? = null,
    @SerializedName("completion_tokens") val completionTokens: Int? = null,
    @SerializedName("total_tokens") val totalTokens: Int? = null
)

/**
 * Model pricing information (per 1M tokens or per minute)
 */
data class ModelPricing(
    val inputPricePer1M: Double = 0.0,  // Price per 1M input tokens
    val outputPricePer1M: Double = 0.0, // Price per 1M output tokens
    val audioPerMinute: Double = 0.0     // Price per minute of audio (for audio-based models)
)

/**
 * Calculate estimated cost based on usage
 */
fun calculateCost(
    modelId: String,
    inputTokens: Long,
    outputTokens: Long,
    audioSeconds: Double
): Double {
    val pricing = getModelPricing(modelId)

    val tokenCost = (inputTokens / 1_000_000.0) * pricing.inputPricePer1M +
                    (outputTokens / 1_000_000.0) * pricing.outputPricePer1M

    val audioCost = (audioSeconds / 60.0) * pricing.audioPerMinute

    return tokenCost + audioCost
}

/**
 * Get pricing for a specific model
 */
fun getModelPricing(modelId: String): ModelPricing {
    return when {
        // OpenAI hosted transcription models
        modelId == "gpt-4o-transcribe" ->
            ModelPricing(audioPerMinute = 0.006)
        modelId == "gpt-4o-mini-transcribe" ->
            ModelPricing(audioPerMinute = 0.003)

        // Whisper model sizes (self-hosted, free)
        modelId == "base" || modelId == "small" || modelId == "medium" ||
        modelId == "large" || modelId == "large-v2" || modelId == "large-v3" ||
        modelId.contains("whisper-tiny", ignoreCase = true) ||
        modelId.contains("whisper-small", ignoreCase = true) ||
        modelId.contains("whisper-base", ignoreCase = true) ->
            ModelPricing(audioPerMinute = 0.0)

        // OpenAI Whisper - $0.006 per minute (exclude self-hosted and groq models)
        modelId.contains("whisper", ignoreCase = true) &&
            !modelId.contains("groq", ignoreCase = true) &&
            !modelId.contains("tiny", ignoreCase = true) &&
            !modelId.contains("small", ignoreCase = true) &&
            modelId != "base" && modelId != "medium" &&
            modelId != "large" && modelId != "large-v2" && modelId != "large-v3" ->
            ModelPricing(audioPerMinute = 0.006)

        // Groq Whisper - Free tier, very low cost
        modelId.contains("groq", ignoreCase = true) || modelId.contains("distil-whisper", ignoreCase = true) ->
            ModelPricing(audioPerMinute = 0.0)

        // GPT-4 models
        modelId.contains("gpt-4o", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 2.50, outputPricePer1M = 10.0)
        modelId.contains("gpt-4-turbo", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 10.0, outputPricePer1M = 30.0)
        modelId.contains("gpt-4", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 30.0, outputPricePer1M = 60.0)

        // GPT-3.5 models
        modelId.contains("gpt-3.5", ignoreCase = true) || modelId.contains("gpt-35", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 0.50, outputPricePer1M = 1.50)

        // Gemini models
        modelId.contains("gemini-2.0-flash", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 0.10, outputPricePer1M = 0.40, audioPerMinute = 0.006)
        modelId.contains("gemini-1.5-flash", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 0.075, outputPricePer1M = 0.30, audioPerMinute = 0.006)
        modelId.contains("gemini-1.5-pro", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 1.25, outputPricePer1M = 5.0, audioPerMinute = 0.03)

        // Claude models
        modelId.contains("claude-3-opus", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 15.0, outputPricePer1M = 75.0)
        modelId.contains("claude-3-sonnet", ignoreCase = true) || modelId.contains("claude-3.5-sonnet", ignoreCase = true) || modelId.contains("claude-3-5-sonnet", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 3.0, outputPricePer1M = 15.0)
        modelId.contains("claude-3-haiku", ignoreCase = true) || modelId.contains("claude-3.5-haiku", ignoreCase = true) || modelId.contains("claude-3-5-haiku", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 0.25, outputPricePer1M = 1.25)

        // DeepSeek models
        modelId.contains("deepseek-chat", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 0.14, outputPricePer1M = 0.28)
        modelId.contains("deepseek-reasoner", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 0.55, outputPricePer1M = 2.19)

        // Groq models (very cheap/free tier)
        modelId.contains("llama-3.3-70b", ignoreCase = true) || modelId.contains("llama-3.1", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 0.05, outputPricePer1M = 0.08)
        modelId.contains("mixtral", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 0.24, outputPricePer1M = 0.24)

        // Deepgram - $0.0043 per minute
        modelId.contains("nova", ignoreCase = true) || modelId.contains("deepgram", ignoreCase = true) ->
            ModelPricing(audioPerMinute = 0.0043)

        // AssemblyAI - ~$0.00025 per second = $0.015 per minute
        modelId.contains("assemblyai", ignoreCase = true) || modelId == "best" || modelId == "nano" ->
            ModelPricing(audioPerMinute = 0.015)

        // Default: assume token-based pricing
        else -> ModelPricing(inputPricePer1M = 1.0, outputPricePer1M = 2.0)
    }
}
