package com.hyperwhisper.data

import com.google.gson.annotations.SerializedName

/**
 * Represents a single API call log entry
 */
data class ApiCallLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val provider: ApiProvider,
    val modelId: String,
    val requestType: String, // "transcription", "chat_completion", "llm_post_process"
    val inputSize: Long, // Audio file size in bytes or text length
    val responseText: String? = null,
    val success: Boolean,
    val errorMessage: String? = null,
    val durationMs: Long = 0,
    val tokenUsage: TokenUsage? = null
)

/**
 * Container for all API call logs
 */
data class ApiCallLogsContainer(
    val logs: List<ApiCallLog> = emptyList()
)
