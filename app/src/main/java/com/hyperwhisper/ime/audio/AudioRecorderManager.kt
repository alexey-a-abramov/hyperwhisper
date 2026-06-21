package com.hyperwhisper.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.PowerManager
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.hyperwhisper.utils.TraceLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Microphone capture, streamed straight to a 16 kHz mono PCM WAV on disk via
 * raw [AudioRecord].
 *
 * Replaces the previous MediaRecorder/M4A implementation: MediaRecorder only
 * surfaced one finished file at stop, which blocked both long recordings and
 * any transcribe-while-recording flow. AudioRecord hands us live PCM frames in
 * a read loop, which (a) lets the recording run far longer (streamed to disk,
 * not buffered) and (b) gives a clean seam to slice chunks for incremental
 * transcription (see the read loop's "Phase 2 seam").
 *
 * The public surface is unchanged so callers (RecordingViewModel,
 * KeyboardViewModel, VoiceRepository) are untouched; the only observable
 * difference is the output file is now `.wav` instead of `.m4a`, which every
 * downstream consumer (cloud ASR, AudioDecoder's WAV fast-path) already handles.
 */
@Singleton
class AudioRecorderManager @Inject constructor(
    private val context: Context
) {
    private var audioRecord: AudioRecord? = null
    private var wavWriter: WavWriter? = null
    private var currentAudioFile: File? = null

    @Volatile
    private var isRecording = false
    private var recordingStartTime: Long = 0
    private var maxDurationNotified = false
    private var timerJob: Job? = null
    private var readJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    private val _recordingCutDueToTimeout = MutableStateFlow(false)
    val recordingCutDueToTimeout: StateFlow<Boolean> = _recordingCutDueToTimeout.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)

    // Callback for when max duration is reached
    var onMaxDurationReached: (() -> Unit)? = null

    companion object {
        private const val TAG = "AudioRecorderManager"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        // ~100ms read frames (16kHz * 2 bytes/sample * 0.1s) — small enough for
        // tight duration tracking and future VAD framing, big enough to avoid
        // busy-looping the read thread.
        private const val READ_FRAME_BYTES = 3200
        // Streaming to disk lifts the old 3-minute cap; keep a generous safety
        // bound so a forgotten recording can't fill storage unbounded.
        const val MAX_RECORDING_DURATION_MS = 1_800_000L // 30 minutes
    }

    /**
     * Start recording audio
     */
    suspend fun startRecording(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isRecording) {
                TraceLogger.trace("AudioRecorder", "Already recording - ignoring start request")
                return@withContext Result.failure(IllegalStateException("Already recording"))
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext Result.failure(
                    SecurityException("RECORD_AUDIO permission not granted")
                )
            }

            TraceLogger.trace("AudioRecorder", "Starting audio recording session")

            val audioFile = File.createTempFile(
                "audio_${System.currentTimeMillis()}",
                ".wav",
                context.cacheDir
            )
            currentAudioFile = audioFile
            TraceLogger.trace("AudioRecorder", "Created temp file: ${audioFile.absolutePath}")

            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_ENCODING)
            if (minBuf <= 0) {
                cleanup()
                return@withContext Result.failure(
                    IOException("AudioRecord.getMinBufferSize failed ($minBuf)")
                )
            }
            // ~1s internal ring buffer (or platform minimum) so a momentarily
            // busy read thread doesn't drop samples.
            val recordBufSize = maxOf(minBuf, SAMPLE_RATE * 2)

            // Try VOICE_RECOGNITION first (tuned for keyboards/background
            // services), fall back to MIC then CAMCORDER.
            val audioSources = listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
                MediaRecorder.AudioSource.MIC to "MIC",
                MediaRecorder.AudioSource.CAMCORDER to "CAMCORDER"
            )

            var record: AudioRecord? = null
            var lastException: Exception? = null
            for ((source, name) in audioSources) {
                try {
                    val r = AudioRecord(source, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_ENCODING, recordBufSize)
                    if (r.state != AudioRecord.STATE_INITIALIZED) {
                        r.release()
                        throw IOException("AudioRecord not initialized for $name")
                    }
                    record = r
                    TraceLogger.trace("AudioRecorder", "AudioRecord ready with $name")
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "AudioRecord failed with $name: ${e.message}")
                    lastException = e
                }
            }

            if (record == null) {
                val msg = "Failed to access microphone with any audio source. " +
                    "Last error: ${lastException?.message}. Ensure microphone permission is " +
                    "granted and no other app holds the mic."
                Log.e(TAG, msg, lastException)
                TraceLogger.error("AudioRecorder", msg, lastException)
                cleanup()
                return@withContext Result.failure(Exception(msg, lastException))
            }

            val writer = WavWriter(audioFile, SAMPLE_RATE, channels = 1, bitsPerSample = 16)

            audioRecord = record
            wavWriter = writer
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            _recordingDuration.value = 0L
            _recordingCutDueToTimeout.value = false
            maxDurationNotified = false

            acquireWakeLock()
            record.startRecording()
            startReadLoop(record, writer)
            startTimer()

            Log.d(TAG, "Recording started: ${audioFile.absolutePath}")
            TraceLogger.trace("AudioRecorder", "Recording started successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting recording", e)
            TraceLogger.error("AudioRecorder", "Unexpected error starting recording", e)
            cleanup()
            Result.failure(e)
        }
    }

    /**
     * Blocking-read loop on an IO thread: pull PCM frames from [record] and
     * append them to [writer] until [isRecording] clears. The current read
     * finishes (≤ one frame) after the flag flips, then the loop exits and
     * [stopRecording] joins it before releasing the device.
     */
    private fun startReadLoop(record: AudioRecord, writer: WavWriter) {
        readJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(READ_FRAME_BYTES)
            try {
                while (isRecording) {
                    val n = record.read(buf, 0, buf.size)
                    when {
                        n > 0 -> {
                            writer.write(buf, n)
                            // Phase 2 seam: hand (buf, n) to the chunk segmenter
                            // here to slice on silence and dispatch ASR live.
                        }
                        n < 0 -> {
                            Log.e(TAG, "AudioRecord.read error ($n)")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Read loop error", e)
            }
        }
    }

    /**
     * Stop recording and return the audio file
     */
    suspend fun stopRecording(): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!isRecording) {
                return@withContext Result.failure(IllegalStateException("Not recording"))
            }

            isRecording = false
            readJob?.join()
            readJob = null

            audioRecord?.let { r ->
                try {
                    r.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping AudioRecord", e)
                }
                r.release()
            }
            audioRecord = null

            wavWriter?.close()
            wavWriter = null

            stopTimer()
            releaseWakeLock()

            val file = currentAudioFile
            currentAudioFile = null
            if (file != null && file.exists() && file.length() > WavWriter.HEADER_SIZE) {
                Log.d(TAG, "Recording stopped: ${file.absolutePath}, size: ${file.length()} bytes")
                Result.success(file)
            } else {
                Result.failure(IOException("Audio file is empty or doesn't exist"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            cleanup()
            Result.failure(e)
        }
    }

    /**
     * Cancel recording and cleanup
     */
    suspend fun cancelRecording() = withContext(Dispatchers.IO) {
        try {
            isRecording = false
            readJob?.join()
            readJob = null
            audioRecord?.let { r ->
                try {
                    r.stop()
                } catch (_: Exception) {
                }
                r.release()
            }
            audioRecord = null
            try {
                wavWriter?.close()
            } catch (_: Exception) {
            }
            wavWriter = null
            stopTimer()
            releaseWakeLock()
            cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling recording", e)
        }
    }

    /**
     * Start recording duration timer
     */
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isRecording) {
                val elapsed = System.currentTimeMillis() - recordingStartTime
                _recordingDuration.value = elapsed

                // Check if max duration reached - notify exactly once per recording.
                // The callback owns stopping the recording via the same path as a
                // manual stop, so the audio file is always handed off for processing.
                if (elapsed >= MAX_RECORDING_DURATION_MS && !maxDurationNotified) {
                    maxDurationNotified = true
                    _recordingCutDueToTimeout.value = true
                    onMaxDurationReached?.invoke()
                    Log.d(TAG, "Max recording duration reached - one-shot stop callback invoked")
                }

                delay(100) // Update every 100ms for smooth timer
            }
        }
    }

    /**
     * Stop recording duration timer
     */
    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _recordingDuration.value = 0L
    }

    /**
     * Acquire wake lock to keep recording during screen lock
     */
    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HyperWhisper::RecordingWakeLock"
            ).apply {
                acquire(MAX_RECORDING_DURATION_MS + 10000) // Extra 10 seconds for safety
            }
            Log.d(TAG, "Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    /**
     * Release wake lock
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
    }

    /**
     * Convert audio file to Base64 string
     */
    suspend fun audioFileToBase64(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                return@withContext Result.failure(IOException("File doesn't exist: ${file.absolutePath}"))
            }

            val bytes = file.readBytes()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Log.d(TAG, "Converted audio to base64, size: ${base64.length} chars")
            Result.success(base64)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting audio to base64", e)
            Result.failure(e)
        }
    }

    /**
     * Get audio format based on file extension
     */
    fun getAudioFormat(file: File): String {
        return when (file.extension.lowercase()) {
            "m4a" -> "mp4"
            "wav" -> "wav"
            "mp3" -> "mp3"
            else -> "wav" // recordings are WAV now
        }
    }

    /**
     * Cleanup temp files
     */
    private fun cleanup() {
        currentAudioFile?.let { file ->
            try {
                if (file.exists()) {
                    file.delete()
                    Log.d(TAG, "Cleaned up audio file: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up audio file", e)
            }
        }
        currentAudioFile = null
    }

    /**
     * Release resources
     */
    fun release() {
        isRecording = false
        readJob?.cancel()
        readJob = null
        audioRecord?.let { r ->
            try {
                r.stop()
            } catch (_: Exception) {
            }
            r.release()
        }
        audioRecord = null
        try {
            wavWriter?.close()
        } catch (_: Exception) {
        }
        wavWriter = null
        cleanup()
    }

    fun isCurrentlyRecording(): Boolean = isRecording

    /**
     * Check if last recording was cut due to timeout
     */
    fun wasRecordingCutDueToTimeout(): Boolean = _recordingCutDueToTimeout.value

    /**
     * Clear the recording cut flag
     */
    fun clearRecordingCutFlag() {
        _recordingCutDueToTimeout.value = false
    }
}
