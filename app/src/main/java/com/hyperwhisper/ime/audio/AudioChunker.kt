package com.hyperwhisper.audio

import kotlin.math.sqrt

/**
 * Streaming silence-boundary segmenter for 16-bit mono PCM.
 *
 * Fed fixed-size PCM frames as a recording streams in, it tracks short-term
 * energy and signals a chunk boundary when a speech run is followed by a
 * long-enough pause (so chunks break on natural silences, never mid-word), or
 * when the current chunk hits [maxChunkMs] (so an unbroken monologue still gets
 * cut and dispatched). The boundaries feed live transcription: each completed
 * chunk can be sent to ASR while recording continues.
 *
 * Pure logic — no Android, no I/O — so it's unit-testable and reusable for both
 * live capture and offline splitting.
 */
class AudioChunker(
    private val sampleRate: Int = 16000,
    /** RMS (0..1) below which a frame counts as silence (~ -38 dBFS). */
    private val silenceRmsThreshold: Double = 0.012,
    /** Minimum speech in a chunk before a silence gap may close it. */
    private val minSpeechMs: Int = 400,
    /** Trailing silence that closes a chunk once it has enough speech. */
    private val silenceHangoverMs: Int = 700,
    /** Hard cap so unbroken speech still gets cut and dispatched. */
    private val maxChunkMs: Int = 24_000,
) {
    private var chunkSamples = 0L
    private var speechSamples = 0L
    private var trailingSilenceSamples = 0L

    /**
     * Feed one frame: [n] bytes of little-endian 16-bit PCM from [pcm]. Returns
     * true when the chunk up to and including this frame is complete and should
     * be cut; the internal counters reset so the next frame starts a fresh
     * chunk.
     */
    fun accept(pcm: ByteArray, n: Int): Boolean {
        val samples = (n / 2).toLong()
        if (samples <= 0L) return false
        chunkSamples += samples
        if (rms16(pcm, n) >= silenceRmsThreshold) {
            speechSamples += samples
            trailingSilenceSamples = 0L
        } else {
            trailingSilenceSamples += samples
        }

        val speechMs = speechSamples * 1000L / sampleRate
        val silenceMs = trailingSilenceSamples * 1000L / sampleRate
        val chunkMs = chunkSamples * 1000L / sampleRate

        val cutOnPause = speechMs >= minSpeechMs && silenceMs >= silenceHangoverMs
        val cutOnLength = chunkMs >= maxChunkMs && speechMs >= minSpeechMs
        if (cutOnPause || cutOnLength) {
            reset()
            return true
        }
        return false
    }

    /** Whether the current (un-emitted) chunk holds any speech — used at stop to
     *  decide if the final partial chunk is worth dispatching. */
    fun hasPendingSpeech(): Boolean = speechSamples > 0L

    fun reset() {
        chunkSamples = 0L
        speechSamples = 0L
        trailingSilenceSamples = 0L
    }

    private fun rms16(pcm: ByteArray, n: Int): Double {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < n) {
            // Little-endian signed 16-bit: high byte keeps its sign, low byte unsigned.
            val sample = (pcm[i].toInt() and 0xff) or (pcm[i + 1].toInt() shl 8)
            val v = sample / 32768.0
            sum += v * v
            count++
            i += 2
        }
        return if (count > 0) sqrt(sum / count) else 0.0
    }
}
