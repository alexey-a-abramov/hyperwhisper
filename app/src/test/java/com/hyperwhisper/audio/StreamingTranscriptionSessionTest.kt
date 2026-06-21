package com.hyperwhisper.audio

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class StreamingTranscriptionSessionTest {

    @Test
    fun stitchesChunksInOrderAndPostProcessesOnce() = runTest {
        var postProcessCalls = 0
        val session = StreamingTranscriptionSession(
            scope = this,
            transcribeChunk = { _, index -> "c$index" },
            postProcess = { text -> postProcessCalls++; text.uppercase() },
        )
        // Delivered out of order (chunks finish ASR whenever).
        session.onChunk(File("c2"), 2)
        session.onChunk(File("c0"), 0)
        session.onChunk(File("c1"), 1)

        assertEquals("C0 C1 C2", session.finalize())
        assertEquals("second-level pass runs exactly once", 1, postProcessCalls)
        assertEquals(3, session.chunkCount())
    }

    @Test
    fun emptyWhenNoChunks() = runTest {
        var postProcessCalls = 0
        val session = StreamingTranscriptionSession(
            scope = this,
            transcribeChunk = { _, _ -> "x" },
            postProcess = { postProcessCalls++; it },
        )
        assertEquals("", session.finalize())
        assertEquals("nothing to post-process", 0, postProcessCalls)
    }

    @Test
    fun skipsBlankAndFailedChunks() = runTest {
        val session = StreamingTranscriptionSession(
            scope = this,
            transcribeChunk = { _, index ->
                when (index) {
                    1 -> "   "                 // blank → dropped
                    2 -> error("asr failed")   // throws → treated as ""
                    else -> "ok$index"
                }
            },
            postProcess = { it },
        )
        session.onChunk(File("0"), 0)
        session.onChunk(File("1"), 1)
        session.onChunk(File("2"), 2)
        session.onChunk(File("3"), 3)
        assertEquals("ok0 ok3", session.finalize())
    }
}
