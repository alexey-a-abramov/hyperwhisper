package com.hyperwhisper.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.hyperwhisper.data.db.FakeUsageStatsDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UsageStatisticsRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    // Cancellable per-test scope; cancelled in tearDown so DataStore's
    // background coroutine can't outlive the TemporaryFolder and throw async
    // (the source of flaky UncaughtExceptionsBeforeTest landing on later tests).
    private val dsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @org.junit.After fun tearDown() {
        // cancel AND join — the DataStore IO coroutine must finish before the
        // TemporaryFolder is deleted, or it errors async onto a later test.
        kotlinx.coroutines.runBlocking {
            val job = dsScope.coroutineContext[kotlinx.coroutines.Job]
            job?.cancel()
            job?.join()
        }
    }

    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = dsScope,
            produceFile = { File(tmp.root, "$name.preferences_pb") },
        )

    @Test fun `legacy stats blob migrates to per-model + totals tables`() = runTest {
        val gson = Gson()
        val migrationStore = store("usage1")
        val legacy = UsageStatistics(
            modelUsage = mapOf(
                "whisper-1" to ModelUsage(inputTokens = 100, outputTokens = 50, totalTokens = 150),
                "gpt-4o-mini" to ModelUsage(inputTokens = 200, outputTokens = 75, totalTokens = 275),
            ),
            totalAudioSeconds = 1234.5,
            totalCharacters = 9999L,
            totalBytes = 88888L,
        )
        migrationStore.edit { prefs ->
            prefs[stringPreferencesKey("usage_statistics")] = gson.toJson(legacy)
        }

        val dao = FakeUsageStatsDao()
        val repo = UsageStatisticsRepository(
            migrationStore = migrationStore,
            gson = gson,
            dao = dao,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
        )

        // Trigger a code path that awaits migration completion.
        repo.recordUsage(
            modelId = "whisper-1",
            inputTokens = 0,
            outputTokens = 0,
            totalTokens = 0,
            audioDurationSeconds = 0.0,
        )

        val stats = repo.usageStatistics.first()
        // Legacy whisper-1 + the no-op record above keeps the existing counts.
        assertEquals(100L, stats.modelUsage["whisper-1"]?.inputTokens)
        assertEquals(50L, stats.modelUsage["whisper-1"]?.outputTokens)
        assertEquals(200L, stats.modelUsage["gpt-4o-mini"]?.inputTokens)
        assertEquals(1234.5, stats.totalAudioSeconds, 0.0001)
        assertEquals(9999L, stats.totalCharacters)
        assertEquals(88888L, stats.totalBytes)

        val rawPrefs = migrationStore.data.first()
        assertNull(rawPrefs[stringPreferencesKey("usage_statistics")])
        assertEquals(true, rawPrefs[booleanPreferencesKey("usage_stats_migrated_v1")])
    }

    @Test fun `recordUsage accumulates per-model totals`() = runTest {
        val migrationStore = store("usage2")
        migrationStore.edit { it[booleanPreferencesKey("usage_stats_migrated_v1")] = true }
        val dao = FakeUsageStatsDao()
        val repo = UsageStatisticsRepository(
            migrationStore = migrationStore,
            gson = Gson(),
            dao = dao,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
        )

        repo.recordUsage("whisper-1", inputTokens = 10, outputTokens = 5, totalTokens = 15, audioDurationSeconds = 1.0)
        repo.recordUsage("whisper-1", inputTokens = 20, outputTokens = 10, totalTokens = 30, audioDurationSeconds = 2.0)
        repo.recordUsage("gpt-4o-mini", inputTokens = 100, outputTokens = 50, totalTokens = 150, audioDurationSeconds = 0.0)

        val stats = repo.usageStatistics.first()
        assertEquals(30L, stats.modelUsage["whisper-1"]?.inputTokens)
        assertEquals(15L, stats.modelUsage["whisper-1"]?.outputTokens)
        assertEquals(45L, stats.modelUsage["whisper-1"]?.totalTokens)
        assertEquals(100L, stats.modelUsage["gpt-4o-mini"]?.inputTokens)
        assertEquals(3.0, stats.totalAudioSeconds, 0.0001)
    }

    @Test fun `clearStatistics empties both tables`() = runTest {
        val migrationStore = store("usage3")
        migrationStore.edit { it[booleanPreferencesKey("usage_stats_migrated_v1")] = true }
        val dao = FakeUsageStatsDao()
        val repo = UsageStatisticsRepository(
            migrationStore = migrationStore,
            gson = Gson(),
            dao = dao,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
        )
        repo.recordUsage("m", 1, 1, 2, 1.0, 1L, 1L)
        repo.clearStatistics()

        val stats = repo.usageStatistics.first()
        assertEquals(0, stats.modelUsage.size)
        assertEquals(0.0, stats.totalAudioSeconds, 0.0001)
        assertEquals(0L, stats.totalCharacters)
    }
}
