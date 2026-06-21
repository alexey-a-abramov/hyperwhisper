package com.hyperwhisper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hyperwhisper.data.db.HistoryDao
import com.hyperwhisper.data.db.toDomain
import com.hyperwhisper.data.db.toEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.historyMigrationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hyperwhisper_history",
)

/**
 * Repository for managing transcription history.
 *
 * Storage moved from a single JSON-blob preference to a Room table — partial
 * mutations (insert/update single row) used to require rewriting the whole
 * list, which scaled poorly under the unlimited-history mode.
 *
 * Legacy data in the previous DataStore is migrated once on first use; the
 * sentinel `history_migrated_v1` short-circuits subsequent boots.
 */
@Singleton
class HistoryRepository(
    private val migrationStore: DataStore<Preferences>,
    private val gson: Gson,
    private val appearanceSettings: Flow<AppearanceSettings>,
    private val historyDao: HistoryDao,
    private val audioDir: File,
    scope: CoroutineScope,
) {
    @Inject constructor(
        @ApplicationContext context: Context,
        gson: Gson,
        appearanceRepository: AppearanceRepository,
        historyDao: HistoryDao,
    ) : this(
        migrationStore = context.historyMigrationDataStore,
        gson = gson,
        appearanceSettings = appearanceRepository.appearanceSettings,
        historyDao = historyDao,
        audioDir = AudioHistoryFiles.dir(context),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    init {
        scope.launch {
            migrateLegacyIfNeeded()
            reconcileAudioFiles()
        }
    }

    /**
     * Transcription History Flow — backed by the Room DAO. Order matches the
     * old behavior (newest first) via SQL `ORDER BY timestamp DESC`.
     */
    val transcriptionHistory: Flow<List<TranscriptionHistoryItem>> =
        historyDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /**
     * Add new item to history.
     * Respects the user's max-history setting and deletes audio files for any
     * trimmed items.
     */
    suspend fun addToHistory(text: String, audioFilePath: String? = null) {
        if (text.isBlank() && audioFilePath == null) return

        migrateLegacyIfNeeded()

        val item = TranscriptionHistoryItem(text = text, audioFilePath = audioFilePath)
        historyDao.upsert(item.toEntity())

        val appearance = appearanceSettings.first()
        if (!appearance.unlimitedHistory) {
            val keep = appearance.maxHistoryItems
            val stale = historyDao.staleAfterTrim(keep)
            stale.forEach { row ->
                row.audioFilePath?.let { path -> runCatching { File(path).delete() } }
            }
            historyDao.trimToSize(keep)
        }
    }

    /**
     * Update an existing history item with new transcription text.
     * Used when reprocessing audio with different settings.
     */
    suspend fun updateHistoryItem(itemId: String, newText: String) {
        historyDao.updateText(itemId, newText, System.currentTimeMillis())
    }

    /**
     * Reconcile the on-disk audio_history directory against the history table.
     *
     *  1. Normalise legacy file names (`audio_<millis>_<uuid>.wav`) to the
     *     current `rec_<datetime>.wav` convention, in place, updating any DB
     *     row that referenced the old path.
     *  2. Drop dangling references — rows pointing at a file that no longer
     *     exists lose their audio path (or are deleted outright if they have
     *     no text either, since an empty + audioless row is unusable).
     *  3. Surface orphans — audio files that exist on disk but aren't linked to
     *     any row (saved during walkie-talkie use, a crash, or before history
     *     linking) get an audio-only history entry so they can be played and
     *     reprocessed instead of silently rotting in storage.
     *
     * Idempotent and safe to call repeatedly (boot + each time the history
     * panel opens).
     */
    suspend fun reconcileAudioFiles() {
        val rows = historyDao.getAll()
        val referenced = HashSet<String>()

        // Pass 1 + 2: walk known rows, fix names, prune dead references.
        for (row in rows) {
            val path = row.audioFilePath ?: continue
            val file = File(path)
            if (!file.exists()) {
                if (row.text.isBlank()) historyDao.deleteById(row.id)
                else historyDao.updateAudioPath(row.id, null)
                continue
            }
            val normalized = ensureConventionalName(file, row.timestamp)
            if (normalized.absolutePath != path) {
                historyDao.updateAudioPath(row.id, normalized.absolutePath)
            }
            referenced.add(normalized.absolutePath)
        }

        // Pass 3: anything on disk not now referenced is an orphan — adopt it.
        val files = audioDir.listFiles()?.filter { it.isFile && it.name.endsWith(".wav") }
            ?: return
        for (file in files) {
            if (file.absolutePath in referenced) continue
            val timestamp = AudioHistoryFiles.timestampFor(file)
            val normalized = ensureConventionalName(file, timestamp)
            if (normalized.absolutePath in referenced) continue
            val item = TranscriptionHistoryItem(
                text = "",
                timestamp = timestamp,
                audioFilePath = normalized.absolutePath,
            )
            historyDao.upsert(item.toEntity())
            referenced.add(normalized.absolutePath)
        }
    }

    /**
     * Rename [file] to the current convention derived from [timestampMs] if it
     * doesn't already match. Returns the (possibly new) file; on any failure
     * the original is returned unchanged so reconciliation can't lose audio.
     */
    private fun ensureConventionalName(file: File, timestampMs: Long): File {
        if (AudioHistoryFiles.matchesConvention(file.name)) return file
        val parent = file.parentFile ?: return file
        var stamp = timestampMs
        var target = File(parent, AudioHistoryFiles.nameFor(stamp))
        var guard = 0
        while (target.exists() && target.absolutePath != file.absolutePath) {
            stamp += 1
            target = File(parent, AudioHistoryFiles.nameFor(stamp))
            if (++guard > 1000) return file
        }
        return if (runCatching { file.renameTo(target) }.getOrDefault(false)) target else file
    }

    /** Clear all history and delete all associated audio files. */
    suspend fun clearHistory() {
        val all = historyDao.getAll()
        all.forEach { row ->
            row.audioFilePath?.let { path -> runCatching { File(path).delete() } }
        }
        historyDao.deleteAll()
    }

    /**
     * Check how many items will be deleted if history size is reduced.
     * Returns 0 when [newUnlimited] is true (nothing deleted).
     */
    suspend fun checkHistorySizeReduction(newMaxSize: Int, newUnlimited: Boolean): Int {
        if (newUnlimited) return 0
        val total = historyDao.count()
        return (total - newMaxSize).coerceAtLeast(0)
    }

    /** Trim history to new size, deleting excess items and their audio files. */
    suspend fun trimHistoryToSize(maxSize: Int) {
        val stale = historyDao.staleAfterTrim(maxSize)
        stale.forEach { row ->
            row.audioFilePath?.let { path -> runCatching { File(path).delete() } }
        }
        historyDao.trimToSize(maxSize)
    }

    /**
     * One-shot copy of the legacy DataStore JSON-blob into Room.
     * Idempotent; the sentinel makes re-runs free.
     */
    private suspend fun migrateLegacyIfNeeded() {
        val prefs = migrationStore.data.first()
        if (prefs[SENTINEL] == true) return

        val json = prefs[LEGACY_HISTORY_KEY]
        if (!json.isNullOrEmpty()) {
            val parsed: List<TranscriptionHistoryItem> = try {
                val type = object : TypeToken<List<TranscriptionHistoryItem>>() {}.type
                gson.fromJson<List<TranscriptionHistoryItem>>(json, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            if (parsed.isNotEmpty()) {
                historyDao.upsertAll(parsed.map { it.toEntity() })
            }
        }
        migrationStore.edit { p ->
            p.remove(LEGACY_HISTORY_KEY)
            p[SENTINEL] = true
        }
    }

    companion object {
        private val LEGACY_HISTORY_KEY = stringPreferencesKey("transcription_history")
        private val SENTINEL = booleanPreferencesKey("history_migrated_v1")
    }
}
