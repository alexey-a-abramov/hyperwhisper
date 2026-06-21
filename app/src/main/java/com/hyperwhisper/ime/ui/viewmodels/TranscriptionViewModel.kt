package com.hyperwhisper.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyperwhisper.data.*
import com.hyperwhisper.data.config.ConfigPatchApplier
import com.hyperwhisper.data.config.ConfigPatchParser
import com.hyperwhisper.data.config.ConfigSnapshotProvider
import com.hyperwhisper.data.config.PendingConfigPatch
import com.hyperwhisper.localization.stringsFor
import com.hyperwhisper.network.VoiceRepository
import com.hyperwhisper.utils.TraceLogger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for managing transcription processing
 * Handles API calls, progress tracking, and result processing
 *
 * Note: Not a @HiltViewModel - created internally by KeyboardViewModel
 */
class TranscriptionViewModel(
    private val context: Context,
    private val voiceRepository: VoiceRepository,
    private val configSnapshotProvider: ConfigSnapshotProvider,
    private val configPatchApplier: ConfigPatchApplier,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "TranscriptionViewModel"
    }

    // Transcription result
    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText.asStateFlow()

    // Processing information (models used, token counts, etc.)
    private val _processingInfo = MutableStateFlow<ProcessingInfo?>(null)
    val processingInfo: StateFlow<ProcessingInfo?> = _processingInfo.asStateFlow()

    // Transcription progress (0.0 to 1.0)
    private val _transcriptionProgress = MutableStateFlow<Float?>(null)
    val transcriptionProgress: StateFlow<Float?> = _transcriptionProgress.asStateFlow()

    // Current processing stage
    private val _processingStage = MutableStateFlow<ProcessingStage?>(null)
    val processingStage: StateFlow<ProcessingStage?> = _processingStage.asStateFlow()

    // Current processing phase for granular UI feedback
    private val _processingPhase = MutableStateFlow(ProcessingPhase.IDLE)
    val processingPhase: StateFlow<ProcessingPhase> = _processingPhase.asStateFlow()

    // Pending configuration patch awaiting user confirmation in the diff sheet.
    // Nothing is persisted until confirmPendingPatch().
    private val _pendingConfigPatch = MutableStateFlow<PendingConfigPatch?>(null)
    val pendingConfigPatch: StateFlow<PendingConfigPatch?> = _pendingConfigPatch.asStateFlow()

    // Audio file info for progress display
    private val _lastAudioFileSize = MutableStateFlow<Long>(0L)
    val lastAudioFileSize: StateFlow<Long> = _lastAudioFileSize.asStateFlow()

    private val _lastAudioDuration = MutableStateFlow<Double>(0.0)
    val lastAudioDuration: StateFlow<Double> = _lastAudioDuration.asStateFlow()

    // Error message specific to transcription
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Job for current transcription (to allow cancellation)
    private var transcriptionJob: kotlinx.coroutines.Job? = null

    /**
     * Process audio file through transcription API
     * Returns the saved audio path or null on error
     */
    suspend fun processAudio(
        audioFile: File,
        mode: VoiceMode,
        settings: ApiSettings,
        saveAudioFile: Boolean
    ): String? {
        return try {
            // Capture audio file info for progress display
            _lastAudioFileSize.value = audioFile.length()
            _lastAudioDuration.value = calculateAudioDuration(audioFile)
            Log.d(TAG, "Audio file info: size=${audioFile.length()} bytes, est duration=${_lastAudioDuration.value}s")

            // Validate settings — but skip the API-key gate when on-device
            // Whisper is active. Local processing never hits the cloud, so
            // demanding a cloud key there would block a fully-configured
            // local-only setup.
            val useLocal = settings.localModelSettings.useLocalWhisper
            if (!useLocal && settings.getCurrentApiKey().isBlank()) {
                _errorMessage.value = "Please configure API key for ${settings.provider.displayName} in settings"
                return null
            }

            Log.d(TAG, "Processing audio with mode: ${mode.name}")
            TraceLogger.trace("TranscriptionViewModel", "Processing audio with mode: ${mode.name}, provider: ${settings.provider}")

            // Optionally save audio file to persistent storage for history playback/reprocessing
            val savedAudioPath = if (saveAudioFile) {
                val path = saveAudioFileToPersistentStorage(audioFile)
                Log.d(TAG, "Audio file saved to: $path")
                path
            } else {
                null
            }

            // Start transcription with progress tracking and cancellation support
            transcriptionJob = viewModelScope.launch {
                try {
                    // Initial stage
                    _processingPhase.value = ProcessingPhase.PREPARING_AUDIO
                    _processingStage.value = ProcessingStage.PREPARING
                    _transcriptionProgress.value = ProcessingStage.PREPARING.progressStart

                    // Estimate-driven progress: pull the historical
                    // ms-per-byte for this provider/model from the API call
                    // log, multiply by the file size, add a 1.5s buffer, and
                    // animate from 0 → ~95% over that wall-clock budget. The
                    // last 5% is reserved so the bar can't sit dead at 100%
                    // before the actual response arrives — when the API
                    // returns the outer code jumps to 100%. Also flips
                    // `processingStage` at rough thirds so the stage label
                    // still has something to say while the bar fills.
                    val estimateMs = settingsRepository.estimateTranscriptionMs(
                        settings = settings,
                        audioFileSize = audioFile.length(),
                        audioDurationMs = (_lastAudioDuration.value * 1000).toLong(),
                    )
                    Log.d(TAG, "Progress estimate: ${estimateMs}ms for ${audioFile.length()} bytes / ${"%.2f".format(_lastAudioDuration.value)}s via ${settings.provider}/${settings.modelId} (local=${settings.localModelSettings.useLocalWhisper})")
                    val progressJob = launch { animateProgressToCap(estimateMs) }

                    // Process audio through API
                    val result = voiceRepository.processAudio(audioFile, mode, settings)

                    // Cancel progress updater and complete
                    progressJob.cancel()
                    _processingPhase.value = ProcessingPhase.COMPLETE
                    _processingStage.value = ProcessingStage.FINISHING
                    _transcriptionProgress.value = 1.0f

                    when (result) {
                        is ApiResult.Success -> {
                            Log.d(TAG, "Transcription successful: ${result.data}")
                            TraceLogger.trace("TranscriptionViewModel", "Transcription successful, length: ${result.data.length} chars")

                            // Check if in configuration mode
                            if (mode.id == "configuration") {
                                // Parse LLM output into a pending config patch
                                processConfigurationResult(result.data)
                                // Don't set transcribed text for configuration commands
                            } else {
                                // Normal transcription mode
                                _transcribedText.value = result.data
                            }

                            _processingInfo.value = result.processingInfo
                            _transcriptionProgress.value = null
                            _processingStage.value = null
                            _processingPhase.value = ProcessingPhase.IDLE
                        }
                        is ApiResult.Error -> {
                            Log.e(TAG, "Transcription failed: ${result.message}")
                            TraceLogger.error("TranscriptionViewModel", "Transcription failed: ${result.message}")
                            _errorMessage.value = "API Error: ${result.message}"
                            _transcriptionProgress.value = null
                            _processingStage.value = null
                            _processingPhase.value = ProcessingPhase.ERROR
                        }
                        is ApiResult.Loading -> {
                            // Should not happen in this flow
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "Transcription cancelled by user")
                    TraceLogger.trace("TranscriptionViewModel", "Transcription cancelled")

                    _transcriptionProgress.value = null
                    _processingStage.value = null
                    _processingPhase.value = ProcessingPhase.IDLE
                    throw e // Re-throw to properly cancel the coroutine
                } catch (e: Exception) {
                    Log.e(TAG, "Error during transcription", e)
                    TraceLogger.error("TranscriptionViewModel", "Transcription error", e)
                    _errorMessage.value = e.message
                    _transcriptionProgress.value = null
                    _processingStage.value = null
                    _processingPhase.value = ProcessingPhase.ERROR
                } finally {
                    transcriptionJob = null
                }
            }

            transcriptionJob?.join()
            savedAudioPath

        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio", e)
            _errorMessage.value = e.message
            null
        }
    }

    /**
     * Parse configuration-mode LLM output into a pending patch. Nothing is
     * applied here — the diff sheet shows the changes and the user confirms
     * via [confirmPendingPatch].
     */
    private suspend fun processConfigurationResult(llmOutput: String) {
        try {
            val strings = stringsFor(settingsRepository.appearanceSettings.first().uiLanguage)
            val snapshot = configSnapshotProvider.current()
            val patch = ConfigPatchParser.parseLlmOutput(llmOutput, snapshot)

            when {
                patch == null || patch.isEmpty -> {
                    Log.w(TAG, "Configuration result had no recognizable changes: ${llmOutput.take(200)}")
                    _errorMessage.value = strings.configNoChangesRecognized
                }
                else -> {
                    _pendingConfigPatch.value = patch
                    Log.d(TAG, "Configuration patch pending: ${patch.valid.size} change(s), ${patch.errors.size} error(s)")
                    TraceLogger.trace(
                        "TranscriptionViewModel",
                        "Configuration pending: ${patch.valid.joinToString { "${it.field.path}=${it.newDisplay}" }}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing configuration result", e)
            TraceLogger.error("TranscriptionViewModel", "Configuration result error", e)
            _errorMessage.value = "Configuration error: ${e.message}"
        }
    }

    /**
     * Cancel ongoing transcription
     */
    fun cancelTranscription() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Canceling transcription...")
                TraceLogger.trace("TranscriptionViewModel", "User cancelled transcription")

                transcriptionJob?.cancel()
                transcriptionJob = null

                _transcriptionProgress.value = null
                _processingStage.value = null
                _processingPhase.value = ProcessingPhase.IDLE
                _errorMessage.value = null

                Log.d(TAG, "Transcription cancelled successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error canceling transcription", e)
                TraceLogger.error("TranscriptionViewModel", "Error cancelling transcription", e)
            }
        }
    }

    // ── External progress driving ────────────────────────────────────────
    // History reprocessing calls VoiceRepository directly (not [processAudio]),
    // so it can't piggyback on the live progress loop. These let an external
    // orchestrator (KeyboardViewModel) drive the same determinate indicator —
    // ring + % + ETA — over the same per-provider time estimate.

    private var progressAnimationJob: kotlinx.coroutines.Job? = null

    /**
     * Animate progress 0 → 95% over [estimateMs], flipping the cosmetic stage
     * labels at rough thirds. Caps at 95% so the bar can't sit dead at 100%
     * before the real result lands. Shared by [processAudio] and the external
     * reprocess path below.
     */
    private suspend fun animateProgressToCap(estimateMs: Long) {
        val tickMs = 100L
        val targetCap = 0.95f
        val started = System.currentTimeMillis()
        val stageBreakpoints = listOf(
            0.0f to (ProcessingStage.PREPARING to ProcessingPhase.PREPARING_AUDIO),
            0.15f to (ProcessingStage.UPLOADING to ProcessingPhase.SENDING_TO_SERVER),
            0.30f to (ProcessingStage.WAITING_API to ProcessingPhase.WAITING_FOR_RESPONSE),
            0.85f to (ProcessingStage.FINISHING to ProcessingPhase.RECEIVING_DATA),
        )
        while (true) {
            val elapsed = System.currentTimeMillis() - started
            val frac = (elapsed.toFloat() / estimateMs.toFloat()).coerceIn(0f, targetCap)
            _transcriptionProgress.value = frac
            val (stage, phase) = stageBreakpoints.last { it.first <= frac }.second
            if (_processingStage.value != stage) _processingStage.value = stage
            if (_processingPhase.value != phase) _processingPhase.value = phase
            kotlinx.coroutines.delay(tickMs)
        }
    }

    /**
     * Begin the determinate progress animation for an inference run happening
     * outside [processAudio] (history reprocessing). Sizes the estimate from
     * [audioFile] exactly like the live path. Pair with [finishProgressAnimation]
     * on success or [cancelProgressAnimation] on failure.
     */
    fun startProgressAnimation(audioFile: File, settings: ApiSettings) {
        progressAnimationJob?.cancel()
        val sizeBytes = runCatching { audioFile.length() }.getOrDefault(0L)
        _lastAudioFileSize.value = sizeBytes
        _lastAudioDuration.value = calculateAudioDuration(audioFile)
        _processingPhase.value = ProcessingPhase.PREPARING_AUDIO
        _processingStage.value = ProcessingStage.PREPARING
        _transcriptionProgress.value = ProcessingStage.PREPARING.progressStart
        progressAnimationJob = viewModelScope.launch {
            val estimateMs = settingsRepository.estimateTranscriptionMs(
                settings = settings,
                audioFileSize = sizeBytes,
                audioDurationMs = (_lastAudioDuration.value * 1000).toLong(),
            )
            animateProgressToCap(estimateMs)
        }
    }

    /** Snap to 100% then clear — the result arrived successfully. */
    fun finishProgressAnimation() {
        progressAnimationJob?.cancel()
        progressAnimationJob = null
        _processingStage.value = ProcessingStage.FINISHING
        _processingPhase.value = ProcessingPhase.COMPLETE
        _transcriptionProgress.value = 1.0f
        viewModelScope.launch {
            kotlinx.coroutines.delay(150)
            _transcriptionProgress.value = null
            _processingStage.value = null
            _processingPhase.value = ProcessingPhase.IDLE
        }
    }

    /** Clear the progress UI immediately without the 100% flash — on failure. */
    fun cancelProgressAnimation() {
        progressAnimationJob?.cancel()
        progressAnimationJob = null
        _transcriptionProgress.value = null
        _processingStage.value = null
        _processingPhase.value = ProcessingPhase.IDLE
    }

    /**
     * Streaming path hooks. [persistAudioForHistory] keeps the full recording
     * for history/reprocess (same store the live path uses); [setStreamedText]
     * publishes the assembled streamed transcript so the screen auto-commits it
     * exactly like a single-shot result.
     */
    fun persistAudioForHistory(audioFile: File): String? = saveAudioFileToPersistentStorage(audioFile)

    fun setStreamedText(text: String) {
        _transcribedText.value = text
    }

    /**
     * Save audio file to persistent storage for reprocessing
     * Returns the absolute path to the saved file, or null on error
     */
    private fun saveAudioFileToPersistentStorage(audioFile: File): String? {
        return try {
            // Create audio history directory
            val audioDir = com.hyperwhisper.data.AudioHistoryFiles.dir(context)
            if (!audioDir.exists()) {
                audioDir.mkdirs()
                Log.d(TAG, "Created audio history directory: ${audioDir.absolutePath}")
            }

            // Name the file from the recording time (see AudioHistoryFiles).
            // Bump the timestamp on the off-chance two saves land in the same
            // millisecond so the name stays unique and still round-trips.
            var stamp = System.currentTimeMillis()
            var destFile = File(audioDir, com.hyperwhisper.data.AudioHistoryFiles.nameFor(stamp))
            while (destFile.exists()) {
                stamp += 1
                destFile = File(audioDir, com.hyperwhisper.data.AudioHistoryFiles.nameFor(stamp))
            }

            // Copy file to persistent storage
            audioFile.copyTo(destFile, overwrite = false)

            Log.d(TAG, "Audio saved to persistent storage: ${destFile.absolutePath}")
            TraceLogger.trace("TranscriptionViewModel", "Audio file saved: ${destFile.name}, size: ${destFile.length()} bytes")

            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save audio file to persistent storage", e)
            TraceLogger.error("TranscriptionViewModel", "Failed to save audio file", e)
            null
        }
    }

    /**
     * Calculate audio duration from file
     * For WAV files, estimates duration based on file size and format assumptions
     */
    private fun calculateAudioDuration(audioFile: File): Double {
        return try {
            // Standard WAV format assumptions for our recorder:
            // 16-bit PCM, mono, 16kHz sample rate
            // Bytes per second = 16000 samples/sec * 2 bytes/sample = 32000 bytes/sec
            val WAV_HEADER_SIZE = 44
            val BYTES_PER_SECOND = 32000.0 // 16kHz * mono * 16-bit (2 bytes)

            val dataSize = audioFile.length() - WAV_HEADER_SIZE
            val duration = dataSize / BYTES_PER_SECOND
            duration.coerceAtLeast(0.0)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating audio duration", e)
            0.0
        }
    }

    /**
     * Clear transcribed text
     */
    fun clearTranscribedText() {
        _transcribedText.value = ""
    }

    /**
     * Clear processing info
     */
    fun clearProcessingInfo() {
        _processingInfo.value = null
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Apply the pending configuration patch. This is the ONLY place voice
     * configuration changes get persisted.
     */
    fun confirmPendingPatch() {
        viewModelScope.launch {
            val pending = _pendingConfigPatch.value ?: return@launch
            val result = configPatchApplier.apply(pending)
            if (result.success) {
                Log.d(TAG, "Configuration patch applied: ${result.appliedCount} change(s)")
                TraceLogger.trace("TranscriptionViewModel", "Configuration applied: ${result.appliedCount} change(s)")
            } else {
                val strings = stringsFor(settingsRepository.appearanceSettings.first().uiLanguage)
                _errorMessage.value = result.errorMessage?.let { "${strings.configApplyFailed}: $it" }
                    ?: strings.configApplyFailed
            }
            _pendingConfigPatch.value = null
        }
    }

    /**
     * Discard the pending configuration patch without applying anything.
     */
    fun rejectPendingPatch() {
        Log.d(TAG, "Configuration patch rejected")
        _pendingConfigPatch.value = null
    }
}
