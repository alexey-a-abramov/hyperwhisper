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

        // Estimator constants — tuned, not a contract. Adjust if real-world
        // timings drift; the historical ratio kicks in after the first
        // successful call so defaults only matter on a cold install.
        private const val SAMPLE_LIMIT = 10
        private const val REQUEST_TYPE_TRANSCRIPTION = "transcription"
        // Fallback when no history exists. ~10 µs/byte ≈ 3 s for ~300 KB,
        // which is roughly a fast cloud Whisper round-trip on a 5 s clip.
        private const val DEFAULT_MS_PER_BYTE = 0.010
        // Used when audioFileSize is unknown (0). 4 s feels long enough that
        // the bar doesn't snap to 100% before the user notices it moved.
        private const val DEFAULT_FALLBACK_MS = 4_000L
        // Safety margin so the bar reaches "almost done" *before* the
        // response usually arrives — better to undershoot the target than
        // park at 100% waiting.
        private const val BUFFER_MS = 1_500L
        // Floor: if the heuristic produces something tiny (very small file +
        // very fast model), keep at least this so the animation has time to
        // be readable.
        private const val MIN_ESTIMATE_MS = 1_500L
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
     * Estimate transcription wall-clock for an upcoming call.
     *
     * Strategy: average ms-per-byte from the last [SAMPLE_LIMIT] *successful*
     * transcription calls of the same provider/model. Multiplies by the
     * upcoming [audioFileSize], adds a [BUFFER_MS] safety margin, and floors
     * at [MIN_ESTIMATE_MS] so the progress bar never sets a target it
     * trivially overshoots before the user sees motion.
     *
     * Falls back to a [DEFAULT_MS_PER_BYTE] heuristic when there's no
     * matching history yet — picked so a typical 5-second voice clip
     * (~300 KB at 48 kHz mono PCM-16) maps to ~3 s, which is in line with
     * fast cloud Whisper providers.
     *
     * Bytes-per-byte rather than seconds-of-audio because the audio length
     * isn't logged today; bytes are. Different providers using different
     * upload formats will simply train their own ratio over the first few
     * calls and converge.
     */
    suspend fun estimateTranscriptionMs(
        provider: ApiProvider,
        modelId: String,
        audioFileSize: Long,
    ): Long {
        if (audioFileSize <= 0) return DEFAULT_FALLBACK_MS
        val samples = try {
            dao.recentSuccessfulFor(
                provider = provider.name,
                modelId = modelId,
                requestType = REQUEST_TYPE_TRANSCRIPTION,
                limit = SAMPLE_LIMIT,
            )
        } catch (e: Exception) {
            Log.w(TAG, "estimateTranscriptionMs: lookup failed, falling back", e)
            emptyList()
        }
        val msPerByte = if (samples.isEmpty()) {
            DEFAULT_MS_PER_BYTE
        } else {
            // Geometric-ish average: protect against one outlier (e.g. cold
            // start / network blip) by trimming the slowest sample when we
            // have ≥4 data points.
            val ratios = samples.map { it.durationMs.toDouble() / it.inputSize.toDouble() }
                .sorted()
            val trimmed = if (ratios.size >= 4) ratios.dropLast(1) else ratios
            trimmed.average()
        }
        val raw = (msPerByte * audioFileSize).toLong()
        return (raw + BUFFER_MS).coerceAtLeast(MIN_ESTIMATE_MS)
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
