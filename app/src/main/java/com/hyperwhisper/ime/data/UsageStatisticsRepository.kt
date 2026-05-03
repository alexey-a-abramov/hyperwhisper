package com.hyperwhisper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.usageStatsDataStore: DataStore<Preferences> by preferencesDataStore(name = "hyperwhisper_usage_stats")

/**
 * Repository for tracking usage statistics
 * Records token usage per model and total audio processing duration
 */
@Singleton
class UsageStatisticsRepository @Inject constructor(
    private val context: Context,
    private val gson: Gson
) {
    private val dataStore = context.usageStatsDataStore

    companion object {
        private val USAGE_STATISTICS_KEY = stringPreferencesKey("usage_statistics")
    }

    /**
     * Usage Statistics Flow
     * Emits current usage statistics including model token counts and audio duration
     */
    val usageStatistics: Flow<UsageStatistics> = dataStore.data.map { preferences ->
        val statsJson = preferences[USAGE_STATISTICS_KEY]
        if (statsJson.isNullOrEmpty()) {
            UsageStatistics()
        } else {
            try {
                gson.fromJson(statsJson, UsageStatistics::class.java) ?: UsageStatistics()
            } catch (e: Exception) {
                UsageStatistics()
            }
        }
    }

    /**
     * Record usage for a transcription
     * Updates token counts for the model and total audio duration
     */
    suspend fun recordUsage(
        modelId: String,
        inputTokens: Int,
        outputTokens: Int,
        totalTokens: Int,
        audioDurationSeconds: Double,
        outputCharacters: Long = 0L,
        outputBytes: Long = 0L
    ) {
        dataStore.edit { preferences ->
            val currentStatsJson = preferences[USAGE_STATISTICS_KEY]
            val currentStats = if (currentStatsJson.isNullOrEmpty()) {
                UsageStatistics()
            } else {
                try {
                    gson.fromJson(currentStatsJson, UsageStatistics::class.java) ?: UsageStatistics()
                } catch (e: Exception) {
                    UsageStatistics()
                }
            }

            // Update model usage
            val currentModelUsage = currentStats.modelUsage[modelId] ?: ModelUsage()
            val newModelUsage = ModelUsage(
                inputTokens = currentModelUsage.inputTokens + inputTokens,
                outputTokens = currentModelUsage.outputTokens + outputTokens,
                totalTokens = currentModelUsage.totalTokens + totalTokens
            )

            val updatedModelUsage = currentStats.modelUsage.toMutableMap()
            updatedModelUsage[modelId] = newModelUsage

            val updatedStats = UsageStatistics(
                modelUsage = updatedModelUsage,
                totalAudioSeconds = currentStats.totalAudioSeconds + audioDurationSeconds,
                totalCharacters = currentStats.totalCharacters + outputCharacters,
                totalBytes = currentStats.totalBytes + outputBytes
            )

            preferences[USAGE_STATISTICS_KEY] = gson.toJson(updatedStats)
        }
    }

    /**
     * Clear all usage statistics
     */
    suspend fun clearStatistics() {
        dataStore.edit { preferences ->
            preferences.remove(USAGE_STATISTICS_KEY)
        }
    }
}
