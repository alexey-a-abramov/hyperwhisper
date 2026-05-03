package com.hyperwhisper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.hyperwhisper.data.db.UsageStatsDao
import com.hyperwhisper.data.db.UsageTotalsEntity
import com.hyperwhisper.data.db.composeUsageStatistics
import com.hyperwhisper.data.db.toEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.usageStatsMigrationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hyperwhisper_usage_stats",
)

/**
 * Repository for tracking usage statistics.
 *
 * Storage moved from a JSON-blob-with-Map<String, ModelUsage> to two Room
 * tables — per-model token totals (one row per modelId, indexed by primary
 * key) and a singleton totals row for cross-session sums. Each [recordUsage]
 * is now an atomic per-row upsert instead of a load-mutate-rewrite.
 *
 * Legacy data is imported once on first use; the sentinel
 * `usage_stats_migrated_v1` short-circuits subsequent boots.
 */
@Singleton
class UsageStatisticsRepository(
    private val migrationStore: DataStore<Preferences>,
    private val gson: Gson,
    private val dao: UsageStatsDao,
    scope: CoroutineScope,
) {
    @Inject constructor(
        @ApplicationContext context: Context,
        gson: Gson,
        dao: UsageStatsDao,
    ) : this(
        migrationStore = context.usageStatsMigrationDataStore,
        gson = gson,
        dao = dao,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    init {
        scope.launch { migrateLegacyIfNeeded() }
    }

    val usageStatistics: Flow<UsageStatistics> = combine(
        dao.observePerModel(),
        dao.observeTotals(),
    ) { perModel, totals ->
        composeUsageStatistics(perModel, totals)
    }

    /**
     * Record usage for a transcription. Updates the per-model token row and
     * the singleton totals row. Each operation is a single SQL upsert.
     */
    suspend fun recordUsage(
        modelId: String,
        inputTokens: Int,
        outputTokens: Int,
        totalTokens: Int,
        audioDurationSeconds: Double,
        outputCharacters: Long = 0L,
        outputBytes: Long = 0L,
    ) {
        migrateLegacyIfNeeded()

        val existingModel = dao.getForModel(modelId)
        val newModel = ModelUsage(
            inputTokens = (existingModel?.inputTokens ?: 0L) + inputTokens,
            outputTokens = (existingModel?.outputTokens ?: 0L) + outputTokens,
            totalTokens = (existingModel?.totalTokens ?: 0L) + totalTokens,
        )
        dao.upsertModelUsage(newModel.toEntity(modelId))

        val existingTotals = dao.getTotals()
        dao.upsertTotals(
            UsageTotalsEntity(
                totalAudioSeconds = (existingTotals?.totalAudioSeconds ?: 0.0) + audioDurationSeconds,
                totalCharacters = (existingTotals?.totalCharacters ?: 0L) + outputCharacters,
                totalBytes = (existingTotals?.totalBytes ?: 0L) + outputBytes,
            ),
        )
    }

    /** Clear all usage statistics (per-model + totals). */
    suspend fun clearStatistics() {
        dao.deleteAllPerModel()
        dao.deleteTotals()
    }

    private suspend fun migrateLegacyIfNeeded() {
        val prefs = migrationStore.data.first()
        if (prefs[SENTINEL] == true) return

        val json = prefs[LEGACY_KEY]
        if (!json.isNullOrEmpty()) {
            val parsed = try {
                gson.fromJson(json, UsageStatistics::class.java) ?: UsageStatistics()
            } catch (_: Exception) {
                UsageStatistics()
            }
            if (parsed.modelUsage.isNotEmpty()) {
                dao.upsertModelUsageAll(parsed.modelUsage.map { (id, u) -> u.toEntity(id) })
            }
            if (parsed.totalAudioSeconds > 0.0 || parsed.totalCharacters > 0 || parsed.totalBytes > 0) {
                dao.upsertTotals(
                    UsageTotalsEntity(
                        totalAudioSeconds = parsed.totalAudioSeconds,
                        totalCharacters = parsed.totalCharacters,
                        totalBytes = parsed.totalBytes,
                    ),
                )
            }
        }
        migrationStore.edit { p ->
            p.remove(LEGACY_KEY)
            p[SENTINEL] = true
        }
    }

    companion object {
        private val LEGACY_KEY = stringPreferencesKey("usage_statistics")
        private val SENTINEL = booleanPreferencesKey("usage_stats_migrated_v1")
    }
}
