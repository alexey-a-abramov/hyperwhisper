package com.hyperwhisper.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.hyperwhisper.data.db.ApiCallLogDao
import com.hyperwhisper.data.db.toDomain
import com.hyperwhisper.data.db.toEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing API call logs.
 *
 * Backed by Room — replaces the previous "load JSON file, mutate the whole
 * list, rewrite" pattern with `INSERT` + a per-(provider, model) trim query.
 *
 * On first use the legacy `filesDir/api_call_logs.json` is read once and
 * imported, then the file is deleted.
 */
@Singleton
class ApiCallLogRepository(
    private val legacyFile: File,
    private val dao: ApiCallLogDao,
    scope: CoroutineScope,
) {
    @Inject constructor(
        @ApplicationContext context: Context,
        dao: ApiCallLogDao,
    ) : this(
        legacyFile = File(context.filesDir, LEGACY_FILE_NAME),
        dao = dao,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    companion object {
        private const val TAG = "ApiCallLogRepository"
        private const val LEGACY_FILE_NAME = "api_call_logs.json"
        private const val MAX_LOGS_PER_MODEL = 20
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    val logs: Flow<List<ApiCallLog>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toDomain() } }

    init {
        scope.launch { migrateLegacyIfNeeded() }
    }

    /**
     * Add a new API call log entry. Each insert is followed by a per-(provider,
     * modelId) trim that retains only the most recent [MAX_LOGS_PER_MODEL] rows
     * for that combination — this keeps the table bounded under heavy use
     * without scanning the whole table.
     */
    suspend fun addLog(log: ApiCallLog) {
        try {
            dao.upsert(log.toEntity())
            dao.trimByProviderModel(
                provider = log.provider.name,
                modelId = log.modelId,
                keep = MAX_LOGS_PER_MODEL,
            )
            Log.d(TAG, "Added API call log for ${log.provider.displayName}/${log.modelId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding API call log", e)
        }
    }

    /** Clear all logs. */
    suspend fun clearAllLogs() {
        try {
            dao.deleteAll()
            Log.d(TAG, "Cleared all API call logs")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing API call logs", e)
        }
    }

    /**
     * Compute statistics from the current set of logs. Suspending so the
     * caller (a ViewModel coroutine) yields rather than blocking.
     */
    suspend fun getStatistics(): ApiCallStatistics {
        val all = dao.getAll().mapNotNull { it.toDomain() }
        val successCount = all.count { it.success }
        val errorCount = all.count { !it.success }
        val totalCalls = all.size
        val modelUsage = all.groupBy { "${it.provider.displayName}/${it.modelId}" }
            .mapValues { it.value.size }
        val averageDuration = if (all.isNotEmpty()) {
            all.map { it.durationMs }.average().toLong()
        } else {
            0L
        }
        return ApiCallStatistics(
            totalCalls = totalCalls,
            successCount = successCount,
            errorCount = errorCount,
            modelUsage = modelUsage,
            averageDurationMs = averageDuration,
        )
    }

    /**
     * One-shot import from `filesDir/api_call_logs.json`. The file is deleted
     * on success so we don't re-import on every boot.
     */
    private suspend fun migrateLegacyIfNeeded() {
        if (!legacyFile.exists()) return
        try {
            val json = legacyFile.readText()
            val container = gson.fromJson(json, ApiCallLogsContainer::class.java)
            val entities = container.logs.map { it.toEntity() }
            if (entities.isNotEmpty()) {
                dao.upsertAll(entities)
                Log.d(TAG, "Migrated ${entities.size} API call log entries from legacy JSON")
            }
            legacyFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error migrating legacy API call log file", e)
            // Best-effort: if migration fails (e.g. corrupted JSON), drop the
            // file so we don't loop forever on every boot.
            runCatching { legacyFile.delete() }
        }
    }
}

/**
 * Statistics about API calls
 */
data class ApiCallStatistics(
    val totalCalls: Int,
    val successCount: Int,
    val errorCount: Int,
    val modelUsage: Map<String, Int>,
    val averageDurationMs: Long,
)
