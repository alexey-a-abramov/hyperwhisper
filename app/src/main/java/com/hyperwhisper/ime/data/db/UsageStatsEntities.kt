package com.hyperwhisper.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per model with cumulative token counts. Replaces the
 * `Map<String, ModelUsage>` JSON blob — partial updates are now a single
 * upsert instead of "load full map, mutate, rewrite".
 */
@Entity(tableName = "usage_stats_per_model")
data class ModelUsageEntity(
    @PrimaryKey val modelId: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
)

/**
 * Single-row table holding the cross-session totals (audio seconds, output
 * char/byte counts). The synthetic primary key [SINGLETON_ID] enforces "always
 * exactly one row" semantics — upserts overwrite the existing values.
 */
@Entity(tableName = "usage_stats_totals")
data class UsageTotalsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val totalAudioSeconds: Double,
    val totalCharacters: Long,
    val totalBytes: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
