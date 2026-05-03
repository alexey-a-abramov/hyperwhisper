package com.hyperwhisper.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted shape of an API call log entry.
 *
 * Mirrors [com.hyperwhisper.data.ApiCallLog]. The provider is stored as its
 * enum name so unknown providers parsed from older data still load (we map
 * them to null or a sentinel at the domain boundary). Token usage is
 * flattened into three nullable columns rather than embedded so simple
 * GROUP BY / aggregate queries work without joins.
 */
@Entity(
    tableName = "api_call_logs",
    indices = [Index(value = ["provider", "modelId"])],
)
data class ApiCallLogEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val provider: String,
    val modelId: String,
    val requestType: String,
    val inputSize: Long,
    val responseText: String?,
    val success: Boolean,
    val errorMessage: String?,
    val durationMs: Long,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
)
