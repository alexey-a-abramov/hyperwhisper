package com.hyperwhisper.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Assembles a streamed dictation from per-chunk transcriptions.
 *
 * As [AudioChunker] cuts the recording into chunks, each finished chunk is
 * handed to [onChunk], which dispatches an ASR-only transcription concurrently
 * (so most of a long recording is transcribed *while* the user is still
 * speaking). At stop, [finalize] waits for every in-flight chunk, stitches the
 * texts back in chunk order, and runs the single second-level pass
 * ([postProcess] — the LLM cleanup/formatting/translation) over the whole
 * transcript exactly once.
 *
 * Dependencies are plain suspend functions so the orchestration is unit-testable
 * without the network/recorder: [transcribeChunk] is ASR-only for one chunk
 * file, [postProcess] is the final whole-transcript pass.
 */
class StreamingTranscriptionSession(
    private val scope: CoroutineScope,
    private val transcribeChunk: suspend (file: File, index: Int) -> String,
    private val postProcess: suspend (fullText: String) -> String,
) {
    private val results = ConcurrentHashMap<Int, String>()
    private val jobs = CopyOnWriteArrayList<Job>()

    @Volatile
    private var maxIndex = -1

    /** A chunk file is ready: kick off its ASR in the background, keyed by order. */
    fun onChunk(file: File, index: Int) {
        if (index > maxIndex) maxIndex = index
        jobs += scope.launch {
            results[index] = runCatching { transcribeChunk(file, index) }.getOrDefault("")
        }
    }

    /**
     * Await all chunk transcriptions, concatenate in chunk order, and run the
     * one final post-process pass. Returns the finished text ("" if nothing was
     * transcribed). Idempotent-ish: safe to call once at stop.
     */
    suspend fun finalize(): String {
        jobs.forEach { it.join() }
        val joined = (0..maxIndex)
            .mapNotNull { results[it]?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString(" ")
            .trim()
        return if (joined.isEmpty()) "" else postProcess(joined)
    }

    /** Number of chunks dispatched so far — for progress display. */
    fun chunkCount(): Int = maxIndex + 1
}
