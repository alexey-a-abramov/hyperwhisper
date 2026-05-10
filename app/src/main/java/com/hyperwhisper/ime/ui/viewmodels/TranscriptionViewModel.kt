package com.hyperwhisper.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyperwhisper.data.*
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
    private val voiceCommandProcessor: VoiceCommandProcessor,
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

    // Pending configuration command for confirmation dialog
    private val _pendingCommandResult = MutableStateFlow<VoiceCommandResult?>(null)
    val pendingCommandResult: StateFlow<VoiceCommandResult?> = _pendingCommandResult.asStateFlow()

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
                        provider = settings.provider,
                        modelId = settings.modelId,
                        audioFileSize = audioFile.length(),
                    )
                    Log.d(TAG, "Progress estimate: ${estimateMs}ms for ${audioFile.length()} bytes via ${settings.provider}/${settings.modelId}")
                    val progressJob = launch {
                        val tickMs = 100L
                        val targetCap = 0.95f
                        val started = System.currentTimeMillis()
                        // Stage labels — purely cosmetic now that progress is
                        // continuous, but they keep the indicator's stage
                        // line meaningful as the bar fills.
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
                            // Pick the latest stage breakpoint we've passed.
                            val (stage, phase) = stageBreakpoints.last { it.first <= frac }.second
                            if (_processingStage.value != stage) _processingStage.value = stage
                            if (_processingPhase.value != phase) _processingPhase.value = phase
                            kotlinx.coroutines.delay(tickMs)
                        }
                    }

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
                                // Process as configuration command
                                processConfigurationCommand(result.data)
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
     * Process configuration command from transcription result
     */
    private suspend fun processConfigurationCommand(commandJson: String) {
        try {
            val commandResult = voiceCommandProcessor.executeCommand(
                commandJson,
                viewModelScope
            )

            if (commandResult.success) {
                // Show pending command for user confirmation
                _pendingCommandResult.value = commandResult
                Log.d(TAG, "Configuration command pending: ${commandResult.message}")
                TraceLogger.trace("TranscriptionViewModel", "Configuration pending: ${commandResult.message}")
            } else {
                // Show error directly
                _errorMessage.value = commandResult.message
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing configuration command", e)
            TraceLogger.error("TranscriptionViewModel", "Configuration command error", e)
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

    /**
     * Save audio file to persistent storage for reprocessing
     * Returns the absolute path to the saved file, or null on error
     */
    private fun saveAudioFileToPersistentStorage(audioFile: File): String? {
        return try {
            // Create audio history directory
            val audioDir = File(context.filesDir, "audio_history")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
                Log.d(TAG, "Created audio history directory: ${audioDir.absolutePath}")
            }

            // Generate unique filename with timestamp
            val filename = "audio_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}.wav"
            val destFile = File(audioDir, filename)

            // Copy file to persistent storage
            audioFile.copyTo(destFile, overwrite = true)

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
     * Confirm and apply pending configuration command
     */
    fun confirmPendingCommand() {
        viewModelScope.launch {
            val pending = _pendingCommandResult.value
            if (pending != null && pending.success) {
                // Show notification
                voiceCommandProcessor.showNotification(pending)
                Log.d(TAG, "Configuration command confirmed: ${pending.message}")
            }
            // Clear pending command
            _pendingCommandResult.value = null
        }
    }

    /**
     * Reject pending configuration command
     */
    fun rejectPendingCommand() {
        Log.d(TAG, "Configuration command rejected")
        _pendingCommandResult.value = null
    }
}
