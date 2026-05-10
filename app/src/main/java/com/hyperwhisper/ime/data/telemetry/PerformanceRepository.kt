package com.hyperwhisper.data.telemetry

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.hyperwhisper.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerformanceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TranscriptionSessionDao,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "PerformanceRepository"
        private const val NINETY_DAYS_MS = 90L * 24L * 60L * 60L * 1000L
    }

    suspend fun recordSession(session: SessionEntity, phases: List<SessionPhaseEntity>) {
        try {
            dao.insertSessionWithPhases(session, phases)
        } catch (t: Throwable) {
            Log.e(TAG, "recordSession failed", t)
        }
    }

    fun recentSessions(limit: Int): Flow<List<SessionWithPhases>> = dao.recentSessions(limit)

    fun latencyRowsSince(sinceEpochMs: Long): Flow<List<SessionLatencyRow>> =
        dao.latencyRowsSince(sinceEpochMs)

    fun totalCount(): Flow<Int> = dao.totalCount()

    suspend fun pruneOlderThan90Days(): Int {
        return try {
            val cutoff = System.currentTimeMillis() - NINETY_DAYS_MS
            dao.pruneOlderThan(cutoff)
        } catch (t: Throwable) {
            Log.e(TAG, "prune failed", t)
            0
        }
    }

    /** Export every session+phases pair as JSONL. Returns the file path on success, or null. */
    suspend fun exportJsonl(): File? {
        return try {
            val target = pickExportFile() ?: return null
            val rows = dao.allSince(0L)
            target.bufferedWriter().use { w ->
                for (sp in rows) {
                    w.write(gson.toJson(SessionExportDto.from(sp)))
                    w.newLine()
                }
            }
            target
        } catch (t: Throwable) {
            Log.e(TAG, "exportJsonl failed", t)
            null
        }
    }

    private fun pickExportFile(): File? {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "telemetry-$ts.jsonl"

        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                if (Environment.isExternalStorageManager()) {
                    val ext = File(Environment.getExternalStorageDirectory(), "apk-logs/HyperWhisper")
                    if (ext.exists() || ext.mkdirs()) return File(ext, name)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "external export path unavailable: ${t.message}")
            }
        }

        return try {
            val dir = File(context.filesDir, "telemetry-export").apply { mkdirs() }
            File(dir, name)
        } catch (t: Throwable) {
            Log.e(TAG, "filesDir export path failed", t)
            null
        }
    }
}

private data class SessionExportDto(
    val session: SessionEntity,
    val phases: List<SessionPhaseEntity>
) {
    companion object {
        fun from(sp: SessionWithPhases) = SessionExportDto(sp.session, sp.phases)
    }
}
