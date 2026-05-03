package com.hyperwhisper.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.hyperwhisper.data.db.FakeHistoryDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { File(tmp.root, "$name.preferences_pb") },
        )

    private fun appearance(max: Int, unlimited: Boolean) =
        MutableStateFlow(AppearanceSettings(maxHistoryItems = max, unlimitedHistory = unlimited))

    @Test fun `legacy json blob is migrated into Room and removed`() = runTest {
        val gson = Gson()
        val migrationStore = store("hist1")
        migrationStore.edit { prefs ->
            prefs[stringPreferencesKey("transcription_history")] = gson.toJson(
                listOf(
                    TranscriptionHistoryItem(id = "a", text = "first", timestamp = 100L),
                    TranscriptionHistoryItem(id = "b", text = "second", timestamp = 200L),
                ),
            )
        }
        val dao = FakeHistoryDao()

        val repo = HistoryRepository(
            migrationStore = migrationStore,
            gson = gson,
            appearanceSettings = appearance(max = 50, unlimited = false),
            historyDao = dao,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
        )

        // Force migration to completion by hitting an entry point that calls it.
        repo.addToHistory(text = "third")

        val list = repo.transcriptionHistory.first()
        assertEquals(3, list.size)
        // Newest first: the freshly added "third" plus the two migrated rows.
        assertTrue(list.any { it.id == "a" })
        assertTrue(list.any { it.id == "b" })

        val rawPrefs = migrationStore.data.first()
        assertNull(rawPrefs[stringPreferencesKey("transcription_history")])
        assertEquals(true, rawPrefs[booleanPreferencesKey("history_migrated_v1")])
    }

    @Test fun `addToHistory respects max size and skips trim under unlimited`() = runTest {
        val dao = FakeHistoryDao()
        val migrationStore = store("hist2")

        // Pre-mark migration as done so it doesn't interfere.
        migrationStore.edit { it[booleanPreferencesKey("history_migrated_v1")] = true }

        val repo = HistoryRepository(
            migrationStore = migrationStore,
            gson = Gson(),
            appearanceSettings = appearance(max = 3, unlimited = false),
            historyDao = dao,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
        )

        repeat(5) { i ->
            repo.addToHistory(text = "msg-$i")
        }

        val limitedList = repo.transcriptionHistory.first()
        assertEquals(3, limitedList.size)

        // Now switch to unlimited and verify nothing is trimmed.
        val unlimitedSettings = appearance(max = 3, unlimited = true)
        val unlimitedRepo = HistoryRepository(
            migrationStore = migrationStore,
            gson = Gson(),
            appearanceSettings = unlimitedSettings,
            historyDao = dao,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
        )
        repeat(5) { i -> unlimitedRepo.addToHistory(text = "u-$i") }
        val all = unlimitedRepo.transcriptionHistory.first()
        assertEquals(8, all.size) // 3 retained + 5 new under unlimited
    }

    @Test fun `clearHistory wipes everything`() = runTest {
        val dao = FakeHistoryDao()
        val migrationStore = store("hist3")
        migrationStore.edit { it[booleanPreferencesKey("history_migrated_v1")] = true }

        val repo = HistoryRepository(
            migrationStore = migrationStore,
            gson = Gson(),
            appearanceSettings = appearance(max = 100, unlimited = false),
            historyDao = dao,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
        )
        repo.addToHistory(text = "a")
        repo.addToHistory(text = "b")
        assertEquals(2, repo.transcriptionHistory.first().size)

        repo.clearHistory()
        assertEquals(0, repo.transcriptionHistory.first().size)
    }
}
