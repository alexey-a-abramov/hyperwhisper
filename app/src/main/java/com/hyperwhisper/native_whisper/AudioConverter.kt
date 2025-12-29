package com.hyperwhisper.native_whisper

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio converter for converting M4A (AAC) to WAV format
 * Required by whisper.cpp which only accepts WAV files
 */
@Singleton
class AudioConverter @Inject constructor() {

    companion object {
        private const val TAG = "AudioConverter"
        private const val TARGET_SAMPLE_RATE = 16000
        private const val TARGET_CHANNELS = 1 // Mono
    }

    /**
     * Convert M4A audio file to WAV format (16kHz, mono, 16-bit PCM)
     * Required by whisper.cpp
     *
     * @param m4aFile Input M4A file
     * @param outputDir Directory to save the output WAV file
     * @return Result containing the WAV file or error
     */
    suspend fun convertM4AToWav(m4aFile: File, outputDir: File): Result<File> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!m4aFile.exists()) {
                return@withContext Result.failure(Exception("M4A file not found"))
            }

            val wavFile = File(outputDir, "${m4aFile.nameWithoutExtension}.wav")
            Log.d(TAG, "Converting ${m4aFile.name} to ${wavFile.name}")

            val extractor = MediaExtractor()
            extractor.setDataSource(m4aFile.absolutePath)

            // Find audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    Log.d(TAG, "Found audio track: $mime")
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                extractor.release()
                return@withContext Result.failure(Exception("No audio track found"))
            }

            extractor.selectTrack(audioTrackIndex)

            // Create decoder
            val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(audioFormat, null, null, 0)
            decoder.start()

            val pcmData = mutableListOf<Short>()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            // Decode audio
            while (!outputDone) {
                // Feed input
                if (!inputDone) {
                    val inputBufferId = decoder.dequeueInputBuffer(10000)
                    if (inputBufferId >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferId)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)

                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputBufferId, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // Get output
                val outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                when {
                    outputBufferId >= 0 -> {
                        val outputBuffer = decoder.getOutputBuffer(outputBufferId)!!

                        if (bufferInfo.size > 0) {
                            // Convert to 16-bit PCM
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.get(chunk)
                            outputBuffer.clear()

                            // Assuming 16-bit PCM output from decoder
                            val shortBuffer = ByteBuffer.wrap(chunk)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .asShortBuffer()

                            while (shortBuffer.hasRemaining()) {
                                pcmData.add(shortBuffer.get())
                            }
                        }

                        decoder.releaseOutputBuffer(outputBufferId, false)

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                    outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = decoder.outputFormat
                        Log.d(TAG, "Output format changed: $newFormat")
                    }
                }
            }

            decoder.stop()
            decoder.release()
            extractor.release()

            // Convert stereo to mono if needed
            val outputFormat = decoder.outputFormat
            val channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)

            Log.d(TAG, "Decoded audio: $sampleRate Hz, $channels channels, ${pcmData.size} samples")

            val monoData = if (channels == 2) {
                Log.d(TAG, "Converting stereo to mono")
                convertStereoToMono(pcmData.toShortArray())
            } else {
                pcmData.toShortArray()
            }

            // Resample if needed (simple decimation/interpolation)
            val finalData = if (sampleRate != TARGET_SAMPLE_RATE) {
                Log.d(TAG, "Resampling from $sampleRate Hz to $TARGET_SAMPLE_RATE Hz")
                resample(monoData, sampleRate, TARGET_SAMPLE_RATE)
            } else {
                monoData
            }

            // Write WAV file
            writeWavFile(wavFile, finalData, TARGET_SAMPLE_RATE, TARGET_CHANNELS)

            Log.d(TAG, "Conversion successful: ${wavFile.absolutePath} (${wavFile.length()} bytes)")
            Result.success(wavFile)

        } catch (e: Exception) {
            Log.e(TAG, "Error converting M4A to WAV", e)
            Result.failure(e)
        }
    }

    /**
     * Convert stereo PCM data to mono by averaging channels
     */
    private fun convertStereoToMono(stereoData: ShortArray): ShortArray {
        val monoData = ShortArray(stereoData.size / 2)
        for (i in monoData.indices) {
            val left = stereoData[i * 2].toInt()
            val right = stereoData[i * 2 + 1].toInt()
            monoData[i] = ((left + right) / 2).toShort()
        }
        return monoData
    }

    /**
     * Simple resampling using linear interpolation
     */
    private fun resample(input: ShortArray, inputRate: Int, outputRate: Int): ShortArray {
        val ratio = inputRate.toDouble() / outputRate.toDouble()
        val outputSize = (input.size / ratio).toInt()
        val output = ShortArray(outputSize)

        for (i in output.indices) {
            val srcIndex = i * ratio
            val srcIndexInt = srcIndex.toInt()

            if (srcIndexInt + 1 < input.size) {
                val fraction = srcIndex - srcIndexInt
                val sample1 = input[srcIndexInt].toDouble()
                val sample2 = input[srcIndexInt + 1].toDouble()
                output[i] = (sample1 + (sample2 - sample1) * fraction).toInt().toShort()
            } else if (srcIndexInt < input.size) {
                output[i] = input[srcIndexInt]
            }
        }

        return output
    }

    /**
     * Write WAV file with standard header
     */
    private fun writeWavFile(
        file: File,
        pcmData: ShortArray,
        sampleRate: Int,
        channels: Int
    ) {
        FileOutputStream(file).use { fos ->
            val dataSize = pcmData.size * 2 // 2 bytes per sample
            val fileSize = 36 + dataSize

            // Write WAV header
            fos.write("RIFF".toByteArray())
            fos.write(intToByteArray(fileSize))
            fos.write("WAVE".toByteArray())
            fos.write("fmt ".toByteArray())
            fos.write(intToByteArray(16)) // fmt chunk size
            fos.write(shortToByteArray(1)) // audio format (PCM)
            fos.write(shortToByteArray(channels.toShort()))
            fos.write(intToByteArray(sampleRate))
            fos.write(intToByteArray(sampleRate * channels * 2)) // byte rate
            fos.write(shortToByteArray((channels * 2).toShort())) // block align
            fos.write(shortToByteArray(16)) // bits per sample
            fos.write("data".toByteArray())
            fos.write(intToByteArray(dataSize))

            // Write PCM data
            val buffer = ByteBuffer.allocate(pcmData.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            pcmData.forEach { buffer.putShort(it) }
            fos.write(buffer.array())
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte(),
            (value shr 16 and 0xFF).toByte(),
            (value shr 24 and 0xFF).toByte()
        )
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            (value.toInt() shr 8 and 0xFF).toByte()
        )
    }
}
