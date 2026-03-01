package com.hyperwhisper.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing API call logs
 * Stores logs in memory and persists to JSON file
 * Automatically rotates logs to keep only the most recent 20 entries per model
 */
@Singleton
class ApiCallLogRepository @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "ApiCallLogRepository"
        private const val LOG_FILE_NAME = "api_call_logs.json"
        private const val MAX_LOGS_PER_MODEL = 20
    }

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private val logFile: File = File(context.filesDir, LOG_FILE_NAME)

    private val _logs = MutableStateFlow<List<ApiCallLog>>(emptyList())
    val logs: Flow<List<ApiCallLog>> = _logs.asStateFlow()

    init {
        loadLogs()
    }

    /**
     * Load logs from persistent storage
     */
    private fun loadLogs() {
        try {
            if (logFile.exists()) {
                val json = logFile.readText()
                val container = gson.fromJson(json, ApiCallLogsContainer::class.java)
                _logs.value = container.logs.sortedByDescending { it.timestamp }
                Log.d(TAG, "Loaded ${_logs.value.size} API call logs")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading API call logs", e)
            _logs.value = emptyList()
        }
    }

    /**
     * Save logs to persistent storage
     */
    private fun saveLogs() {
        try {
            val container = ApiCallLogsContainer(logs = _logs.value)
            val json = gson.toJson(container)
            logFile.writeText(json)
            Log.d(TAG, "Saved ${_logs.value.size} API call logs")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving API call logs", e)
        }
    }

    /**
     * Add a new API call log entry
     * Automatically rotates logs to keep only 20 most recent per model
     */
    suspend fun addLog(log: ApiCallLog) {
        try {
            val currentLogs = _logs.value.toMutableList()
            currentLogs.add(log)

            // Rotate logs: keep only 20 most recent per model
            val rotatedLogs = rotateLogs(currentLogs)

            _logs.value = rotatedLogs.sortedByDescending { it.timestamp }
            saveLogs()

            Log.d(TAG, "Added API call log for ${log.provider.displayName}/${log.modelId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding API call log", e)
        }
    }

    /**
     * Rotate logs to keep only the most recent MAX_LOGS_PER_MODEL entries per model
     */
    private fun rotateLogs(logs: List<ApiCallLog>): List<ApiCallLog> {
        // Group by provider + model
        val groupedLogs = logs.groupBy { "${it.provider.name}/${it.modelId}" }

        // Keep only the most recent 20 per group
        val rotatedLogs = mutableListOf<ApiCallLog>()
        groupedLogs.forEach { (key, entries) ->
            val kept = entries.sortedByDescending { it.timestamp }.take(MAX_LOGS_PER_MODEL)
            rotatedLogs.addAll(kept)
            if (entries.size > MAX_LOGS_PER_MODEL) {
                Log.d(TAG, "Rotated logs for $key: kept ${kept.size}, removed ${entries.size - kept.size}")
            }
        }

        return rotatedLogs
    }

    /**
     * Get logs for a specific provider
     */
    fun getLogsForProvider(provider: ApiProvider): List<ApiCallLog> {
        return _logs.value.filter { it.provider == provider }
    }

    /**
     * Get logs for a specific model
     */
    fun getLogsForModel(provider: ApiProvider, modelId: String): List<ApiCallLog> {
        return _logs.value.filter { it.provider == provider && it.modelId == modelId }
    }

    /**
     * Get grouped logs by model
     */
    fun getLogsGroupedByModel(): Map<String, List<ApiCallLog>> {
        return _logs.value.groupBy { "${it.provider.displayName} / ${it.modelId}" }
    }

    /**
     * Clear all logs
     */
    suspend fun clearAllLogs() {
        try {
            _logs.value = emptyList()
            if (logFile.exists()) {
                logFile.delete()
            }
            Log.d(TAG, "Cleared all API call logs")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing API call logs", e)
        }
    }

    /**
     * Get statistics about API calls
     */
    fun getStatistics(): ApiCallStatistics {
        val allLogs = _logs.value
        val successCount = allLogs.count { it.success }
        val errorCount = allLogs.count { !it.success }
        val totalCalls = allLogs.size

        val modelUsage = allLogs.groupBy { "${it.provider.displayName}/${it.modelId}" }
            .mapValues { it.value.size }

        val averageDuration = if (allLogs.isNotEmpty()) {
            allLogs.map { it.durationMs }.average().toLong()
        } else {
            0L
        }

        return ApiCallStatistics(
            totalCalls = totalCalls,
            successCount = successCount,
            errorCount = errorCount,
            modelUsage = modelUsage,
            averageDurationMs = averageDuration
        )
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
    val averageDurationMs: Long
)
