package com.hyperwhisper.ime.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decode any container/codec the platform's MediaCodec supports (M4A/AAC,
 * MP3, OGG, WAV) into 16 kHz mono float32 PCM, the contract whisper.cpp's
 * `whisper_full` expects.
 *
 * Pipeline: MediaExtractor → MediaCodec(decoder) → int16 little-endian PCM →
 * downmix to mono (averaging channels) → linear-resample to 16 kHz → float32
 * normalized [-1.0, 1.0]. WAV PCM is detected and read directly to skip the
 * codec round-trip.
 */
object AudioDecoder {
    private const val TAG = "AudioDecoder"
    const val WHISPER_SAMPLE_RATE = 16000
    private const val DEQUEUE_TIMEOUT_US = 10_000L

    fun decodeTo16kMonoFloat(file: File): FloatArray {
        // Cheap path: a 16 kHz mono 16-bit canonical WAV (e.g. our bundled
        // sample) is already in whisper's exact format.
        if (file.extension.equals("wav", ignoreCase = true)) {
            readWavRaw(file)?.let { return it }
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val track = findAudioTrack(extractor)
                ?: throw IllegalStateException("No audio track in ${file.name}")
            extractor.selectTrack(track.index)
            val pcmBytes = decodeViaMediaCodec(extractor, track.format)
            val mono = pcm16leToMonoFloat32(pcmBytes, track.channels)
            return if (track.sampleRate == WHISPER_SAMPLE_RATE) mono
            else linearResample(mono, track.sampleRate, WHISPER_SAMPLE_RATE)
        } finally {
            runCatching { extractor.release() }
        }
    }

    private data class TrackInfo(
        val index: Int,
        val format: MediaFormat,
        val sampleRate: Int,
        val channels: Int,
        val mime: String
    )

    private fun findAudioTrack(extractor: MediaExtractor): TrackInfo? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (!mime.startsWith("audio/")) continue
            val sr = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val ch = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            return TrackInfo(i, format, sr, ch, mime)
        }
        return null
    }

    private fun decodeViaMediaCodec(extractor: MediaExtractor, format: MediaFormat): ByteArray {
        val mime = format.getString(MediaFormat.KEY_MIME)
            ?: throw IllegalStateException("Track missing MIME")
        val codec = MediaCodec.createDecoderByType(mime)
        try {
            codec.configure(format, null, null, 0)
            codec.start()

            val pcm = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inIdx = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEOS = true
                        } else {
                            val time = extractor.sampleTime
                            codec.queueInputBuffer(inIdx, 0, sampleSize, time, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                when {
                    outIdx >= 0 -> {
                        if (bufferInfo.size > 0) {
                            val outBuf = codec.getOutputBuffer(outIdx)!!
                            val data = ByteArray(bufferInfo.size)
                            outBuf.position(bufferInfo.offset)
                            outBuf.get(data, 0, bufferInfo.size)
                            pcm.write(data)
                        }
                        codec.releaseOutputBuffer(outIdx, /*render=*/ false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEOS = true
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                        Log.d(TAG, "Output format changed: ${codec.outputFormat}")
                    // INFO_TRY_AGAIN_LATER: just spin, we'll get more later.
                }
            }

            codec.stop()
            return pcm.toByteArray()
        } finally {
            runCatching { codec.release() }
        }
    }

    private fun pcm16leToMonoFloat32(pcm: ByteArray, channels: Int): FloatArray {
        val sampleCount = pcm.size / 2
        val shorts = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        if (channels <= 1) {
            return FloatArray(sampleCount) { shorts.get(it) / 32768f }
        }
        val frames = sampleCount / channels
        return FloatArray(frames) { f ->
            var sum = 0f
            val base = f * channels
            for (c in 0 until channels) sum += shorts.get(base + c) / 32768f
            sum / channels
        }
    }

    /**
     * Linear interpolation resampler. Cheap and good enough for ASR — whisper's
     * own log-mel spectrogram is way more sensitive to clip duration than to
     * resampler quality. For pristine results upstream uses libsoxr; we don't.
     */
    private fun linearResample(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (srcRate == dstRate) return input
        val ratio = srcRate.toDouble() / dstRate
        val outSize = (input.size / ratio).toInt().coerceAtLeast(0)
        val out = FloatArray(outSize)
        val maxIdx = input.size - 1
        for (i in 0 until outSize) {
            val srcIdx = i * ratio
            val lo = srcIdx.toInt().coerceAtMost(maxIdx)
            val hi = (lo + 1).coerceAtMost(maxIdx)
            val t = (srcIdx - lo).toFloat()
            out[i] = input[lo] * (1f - t) + input[hi] * t
        }
        return out
    }

    /**
     * Read a canonical 16 kHz mono 16-bit PCM WAV directly. Returns null for
     * any other WAV layout (compressed, multichannel, weird sample rate) — caller
     * falls back to MediaCodec.
     */
    private fun readWavRaw(file: File): FloatArray? {
        return try {
            val bytes = file.readBytes()
            if (bytes.size < 44) return null
            if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
                bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte()) return null
            val channels = (bytes[22].toInt() and 0xFF) or ((bytes[23].toInt() and 0xFF) shl 8)
            val sampleRate = (bytes[24].toInt() and 0xFF) or
                ((bytes[25].toInt() and 0xFF) shl 8) or
                ((bytes[26].toInt() and 0xFF) shl 16) or
                ((bytes[27].toInt() and 0xFF) shl 24)
            val bits = (bytes[34].toInt() and 0xFF) or ((bytes[35].toInt() and 0xFF) shl 8)
            if (channels != 1 || sampleRate != WHISPER_SAMPLE_RATE || bits != 16) return null
            val n = (bytes.size - 44) / 2
            FloatArray(n) { i ->
                val di = 44 + i * 2
                val lo = bytes[di].toInt() and 0xFF
                val hi = bytes[di + 1].toInt()
                ((hi shl 8) or lo) / 32768f
            }
        } catch (t: Throwable) {
            Log.w(TAG, "WAV fast-path failed: ${t.message}")
            null
        }
    }
}
