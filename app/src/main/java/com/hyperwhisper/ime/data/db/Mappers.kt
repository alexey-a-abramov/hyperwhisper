package com.hyperwhisper.data.db

import com.hyperwhisper.data.ApiCallLog
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ModelUsage
import com.hyperwhisper.data.TokenUsage
import com.hyperwhisper.data.TranscriptionHistoryItem
import com.hyperwhisper.data.UsageStatistics

// -- History --

fun TranscriptionHistoryItem.toEntity() = HistoryEntity(
    id = id,
    text = text,
    timestamp = timestamp,
    audioFilePath = audioFilePath,
)

fun HistoryEntity.toDomain() = TranscriptionHistoryItem(
    id = id,
    text = text,
    timestamp = timestamp,
    audioFilePath = audioFilePath,
)

// -- API call log --

fun ApiCallLog.toEntity() = ApiCallLogEntity(
    id = id,
    timestamp = timestamp,
    provider = provider.name,
    modelId = modelId,
    requestType = requestType,
    inputSize = inputSize,
    responseText = responseText,
    success = success,
    errorMessage = errorMessage,
    durationMs = durationMs,
    promptTokens = tokenUsage?.promptTokens,
    completionTokens = tokenUsage?.completionTokens,
    totalTokens = tokenUsage?.totalTokens,
)

/**
 * Returns null if [provider] is no longer a known [ApiProvider] — older logs
 * from a removed provider become invisible rather than crashing the UI.
 */
fun ApiCallLogEntity.toDomain(): ApiCallLog? {
    val parsedProvider = runCatching { ApiProvider.valueOf(provider) }.getOrNull() ?: return null
    val tokens = if (promptTokens != null || completionTokens != null || totalTokens != null) {
        TokenUsage(
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
        )
    } else {
        null
    }
    return ApiCallLog(
        id = id,
        timestamp = timestamp,
        provider = parsedProvider,
        modelId = modelId,
        requestType = requestType,
        inputSize = inputSize,
        responseText = responseText,
        success = success,
        errorMessage = errorMessage,
        durationMs = durationMs,
        tokenUsage = tokens,
    )
}

// -- Usage stats --

fun ModelUsage.toEntity(modelId: String) = ModelUsageEntity(
    modelId = modelId,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    totalTokens = totalTokens,
)

fun ModelUsageEntity.toDomain() = ModelUsage(
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    totalTokens = totalTokens,
)

fun composeUsageStatistics(
    perModel: List<ModelUsageEntity>,
    totals: UsageTotalsEntity?,
): UsageStatistics = UsageStatistics(
    modelUsage = perModel.associate { it.modelId to it.toDomain() },
    totalAudioSeconds = totals?.totalAudioSeconds ?: 0.0,
    totalCharacters = totals?.totalCharacters ?: 0L,
    totalBytes = totals?.totalBytes ?: 0L,
)
