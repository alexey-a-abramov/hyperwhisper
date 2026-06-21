package com.hyperwhisper.audio

import java.io.File
import java.io.RandomAccessFile

/**
 * Streaming little-endian PCM WAV writer.
 *
 * Writes a 44-byte canonical header with placeholder sizes up front, appends
 * 16-bit PCM frames as they arrive, and patches the two size fields on [close].
 * This lets an arbitrarily long recording stream straight to disk — no
 * in-memory buffering, no duration cap — while the partial file on disk is
 * always a structurally valid WAV (the size fields are just stale until close),
 * so a crash mid-recording still leaves a recoverable file.
 *
 * Single-writer only: all [write] calls and the final [close] must come from
 * the same thread (the recorder's read loop, joined before close).
 */
class WavWriter(
    private val file: File,
    private val sampleRate: Int = 16000,
    private val channels: Int = 1,
    private val bitsPerSample: Int = 16,
) {
    private val raf = RandomAccessFile(file, "rw")
    private var dataBytes = 0L

    init {
        raf.setLength(0)
        raf.write(header(dataLen = 0))
    }

    /** Append [length] bytes of little-endian 16-bit PCM from [pcm]. */
    fun write(pcm: ByteArray, length: Int) {
        if (length <= 0) return
        raf.write(pcm, 0, length)
        dataBytes += length
    }

    /** Bytes of PCM written so far (excludes the 44-byte header). */
    fun bytesWritten(): Long = dataBytes

    /** Patch the RIFF/data size fields to the real totals and close the file. */
    fun close() {
        try {
            // ChunkSize @ offset 4 = 36 + dataBytes
            raf.seek(4)
            raf.write(intLe((36 + dataBytes).toInt()))
            // Subchunk2Size @ offset 40 = dataBytes
            raf.seek(40)
            raf.write(intLe(dataBytes.toInt()))
        } finally {
            raf.close()
        }
    }

    private fun header(dataLen: Int): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val h = ByteArray(HEADER_SIZE)
        // RIFF chunk
        "RIFF".toByteArray().copyInto(h, 0)
        intLe(36 + dataLen).copyInto(h, 4)
        "WAVE".toByteArray().copyInto(h, 8)
        // fmt subchunk
        "fmt ".toByteArray().copyInto(h, 12)
        intLe(16).copyInto(h, 16)            // Subchunk1Size (PCM)
        shortLe(1).copyInto(h, 20)           // AudioFormat = PCM
        shortLe(channels).copyInto(h, 22)
        intLe(sampleRate).copyInto(h, 24)
        intLe(byteRate).copyInto(h, 28)
        shortLe(blockAlign).copyInto(h, 32)
        shortLe(bitsPerSample).copyInto(h, 34)
        // data subchunk
        "data".toByteArray().copyInto(h, 36)
        intLe(dataLen).copyInto(h, 40)
        return h
    }

    private fun intLe(v: Int) = byteArrayOf(
        (v and 0xff).toByte(),
        ((v shr 8) and 0xff).toByte(),
        ((v shr 16) and 0xff).toByte(),
        ((v shr 24) and 0xff).toByte(),
    )

    private fun shortLe(v: Int) = byteArrayOf(
        (v and 0xff).toByte(),
        ((v shr 8) and 0xff).toByte(),
    )

    companion object {
        const val HEADER_SIZE = 44
    }
}
