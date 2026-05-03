package com.hyperwhisper.data

import com.google.gson.GsonBuilder
import com.hyperwhisper.data.db.FakeApiCallLogDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ApiCallLogRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `legacy json file is imported and deleted`() = runTest {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val legacy = File(tmp.root, "api_call_logs.json")
        val seedLogs = (1..3).map { i ->
            ApiCallLog(
                id = "log-$i",
                timestamp = i * 100L,
                provider = ApiProvider.OPENAI,
                modelId = "whisper-1",
                requestType = "transcription",
                inputSize = 1024L,
                success = true,
                durationMs = 250L,
            )
        }
        legacy.writeText(gson.toJson(ApiCallLogsContainer(logs = seedLogs)))

        val dao = FakeApiCallLogDao()
        val repo = ApiCallLogRepository(
            legacyFile = legacy,
            dao = dao,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
        )

        // Drive any code path so migration completes deterministically.
        repo.addLog(seedLogs.first().copy(id = "fresh"))

        val all = repo.logs.first()
        assertTrue(all.size >= 3)
        assertFalse("Legacy file must be removed after migration", legacy.exists())
    }

    @Test fun `addLog enforces 20-per-(provider, model) cap`() = runTest {
        val dao = FakeApiCallLogDao()
        val noFile = File(tmp.root, "missing.json")
        val repo = ApiCallLogRepository(
            legacyFile = noFile,
            dao = dao,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
        )

        // 25 logs for one (provider, model) combo and 5 for another — only the
        // first combo should be trimmed to 20.
        repeat(25) { i ->
            repo.addLog(
                ApiCallLog(
                    id = "openai-$i",
                    timestamp = i.toLong(),
                    provider = ApiProvider.OPENAI,
                    modelId = "whisper-1",
                    requestType = "transcription",
                    inputSize = 0L,
                    success = true,
                    durationMs = 0L,
                ),
            )
        }
        repeat(5) { i ->
            repo.addLog(
                ApiCallLog(
                    id = "groq-$i",
                    timestamp = (1000 + i).toLong(),
                    provider = ApiProvider.GROQ,
                    modelId = "whisper-large-v3",
                    requestType = "transcription",
                    inputSize = 0L,
                    success = true,
                    durationMs = 0L,
                ),
            )
        }

        val all = repo.logs.first()
        val openai = all.count { it.provider == ApiProvider.OPENAI && it.modelId == "whisper-1" }
        val groq = all.count { it.provider == ApiProvider.GROQ && it.modelId == "whisper-large-v3" }
        assertEquals(20, openai)
        assertEquals(5, groq)
    }

    @Test fun `getStatistics aggregates success and total counts`() = runTest {
        val dao = FakeApiCallLogDao()
        val repo = ApiCallLogRepository(
            legacyFile = File(tmp.root, "missing.json"),
            dao = dao,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
        )
        repo.addLog(makeLog(success = true, durationMs = 100))
        repo.addLog(makeLog(success = true, durationMs = 200))
        repo.addLog(makeLog(success = false, durationMs = 300, errorMessage = "boom"))

        val stats = repo.getStatistics()
        assertEquals(3, stats.totalCalls)
        assertEquals(2, stats.successCount)
        assertEquals(1, stats.errorCount)
        assertEquals(200, stats.averageDurationMs)
    }

    @Test fun `clearAllLogs empties the store`() = runTest {
        val dao = FakeApiCallLogDao()
        val repo = ApiCallLogRepository(
            legacyFile = File(tmp.root, "missing.json"),
            dao = dao,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
        )
        repeat(3) { repo.addLog(makeLog(success = true, durationMs = 1)) }
        repo.clearAllLogs()
        assertEquals(0, repo.logs.first().size)
    }

    private fun makeLog(success: Boolean, durationMs: Long, errorMessage: String? = null) =
        ApiCallLog(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            provider = ApiProvider.OPENAI,
            modelId = "whisper-1",
            requestType = "transcription",
            inputSize = 0L,
            success = success,
            errorMessage = errorMessage,
            durationMs = durationMs,
        )
}
