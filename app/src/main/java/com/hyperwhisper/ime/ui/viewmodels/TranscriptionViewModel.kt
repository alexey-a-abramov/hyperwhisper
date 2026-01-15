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
    private val voiceCommandProcessor: VoiceCommandProcessor
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
        settings: ApiSettings
    ): String? {
        return try {
            // Capture audio file info for progress display
            _lastAudioFileSize.value = audioFile.length()
            _lastAudioDuration.value = calculateAudioDuration(audioFile)
            Log.d(TAG, "Audio file info: size=${audioFile.length()} bytes, est duration=${_lastAudioDuration.value}s")

            // Validate settings
            if (settings.getCurrentApiKey().isBlank()) {
                _errorMessage.value = "Please configure API key for ${settings.provider.displayName} in settings"
                return null
            }

            Log.d(TAG, "Processing audio with mode: ${mode.name}")
            TraceLogger.trace("TranscriptionViewModel", "Processing audio with mode: ${mode.name}, provider: ${settings.provider}")

            // Save audio file to persistent storage
            val savedAudioPath = saveAudioFileToPersistentStorage(audioFile)
            Log.d(TAG, "Audio file saved to: $savedAudioPath")

            // Start transcription with progress tracking and cancellation support
            transcriptionJob = viewModelScope.launch {
                try {
                    // Initial stage
                    _processingStage.value = ProcessingStage.PREPARING
                    _transcriptionProgress.value = ProcessingStage.PREPARING.progressStart

                    // Launch progress updater with realistic stage-based simulation
                    val progressJob = launch {
                        // Cloud processing stages: prepare -> upload -> wait for API -> finish
                        val stages = listOf(
                            ProcessingStage.PREPARING to 200L,
                            ProcessingStage.UPLOADING to 800L,
                            ProcessingStage.WAITING_API to 2000L,  // Main API call - takes longest
                            ProcessingStage.FINISHING to 200L
                        )

                        for ((stage, duration) in stages) {
                            _processingStage.value = stage
                            // Smoothly animate progress within the stage
                            val steps = (duration / 100).toInt().coerceAtLeast(1)
                            val progressIncrement = (stage.progressEnd - stage.progressStart) / steps
                            for (i in 0 until steps) {
                                _transcriptionProgress.value = stage.progressStart + (progressIncrement * i)
                                kotlinx.coroutines.delay(100)
                            }
                        }
                    }

                    // Process audio through API
                    val result = voiceRepository.processAudio(audioFile, mode, settings)

                    // Cancel progress updater and complete
                    progressJob.cancel()
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
                        }
                        is ApiResult.Error -> {
                            Log.e(TAG, "Transcription failed: ${result.message}")
                            TraceLogger.error("TranscriptionViewModel", "Transcription failed: ${result.message}")
                            _errorMessage.value = "API Error: ${result.message}"
                            _transcriptionProgress.value = null
                            _processingStage.value = null
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
                    throw e // Re-throw to properly cancel the coroutine
                } catch (e: Exception) {
                    Log.e(TAG, "Error during transcription", e)
                    TraceLogger.error("TranscriptionViewModel", "Transcription error", e)
                    _errorMessage.value = e.message
                    _transcriptionProgress.value = null
                    _processingStage.value = null
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
