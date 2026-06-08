package com.hyperwhisper.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyperwhisper.data.RecordingState
import com.hyperwhisper.network.VoiceRepository
import com.hyperwhisper.utils.TraceLogger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for managing audio recording state
 * Handles recording lifecycle. The 3-minute timeout is owned by
 * AudioRecorderManager, which notifies KeyboardViewModel exactly once;
 * this class must not auto-stop on duration itself.
 *
 * Note: Not a @HiltViewModel - created internally by KeyboardViewModel
 */
class RecordingViewModel(
    private val voiceRepository: VoiceRepository
) : ViewModel() {

    data class StopRecordingResult(val audioFile: File)

    companion object {
        private const val TAG = "RecordingViewModel"
    }

    // Recording state
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    // Error message specific to recording
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Recording duration from repository
    val recordingDuration: StateFlow<Long> = voiceRepository.getRecordingDuration()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Recorded audio file result
    private val _recordedAudioFile = MutableStateFlow<File?>(null)
    val recordedAudioFile: StateFlow<File?> = _recordedAudioFile.asStateFlow()

    // Walkie-talkie mode state
    private val _walkieTalkieMode = MutableStateFlow(false)
    val walkieTalkieMode: StateFlow<Boolean> = _walkieTalkieMode.asStateFlow()

    // Mode change message for UI
    private val _modeChangeMessage = MutableStateFlow<String?>(null)
    val modeChangeMessage: StateFlow<String?> = _modeChangeMessage.asStateFlow()

    // Flag for when recording was cut due to timeout
    private val _recordingWasCut = MutableStateFlow(false)
    val recordingWasCut: StateFlow<Boolean> = _recordingWasCut.asStateFlow()

    // Legacy flag kept for compatibility with existing UI wiring (always false now)
    private val _needsConfirmation = MutableStateFlow(false)
    val needsConfirmation: StateFlow<Boolean> = _needsConfirmation.asStateFlow()

    // Show confirmation when canceling a long recording
    private val _showCancelConfirmation = MutableStateFlow(false)
    val showCancelConfirmation: StateFlow<Boolean> = _showCancelConfirmation.asStateFlow()

    // Final recording duration when stopped
    private val _finalRecordingDuration = MutableStateFlow(0L)
    val finalRecordingDuration: StateFlow<Long> = _finalRecordingDuration.asStateFlow()

    /**
     * Start recording audio
     */
    fun startRecording() {
        viewModelScope.launch {
            try {
                val currentState = _recordingState.value
                if (currentState == RecordingState.RECORDING || currentState == RecordingState.PROCESSING) {
                    Log.d(TAG, "Ignoring startRecording: state=$currentState")
                    return@launch
                }

                if (voiceRepository.isRecording()) {
                    Log.d(TAG, "Recorder is already active in repository, syncing state")
                    _recordingState.value = RecordingState.RECORDING
                    return@launch
                }

                Log.d(TAG, "Starting recording...")
                TraceLogger.trace("RecordingViewModel", "User tapped mic - starting recording")
                _recordingState.value = RecordingState.RECORDING
                _errorMessage.value = null
                _recordedAudioFile.value = null
                _needsConfirmation.value = false
                _finalRecordingDuration.value = 0L

                val result = voiceRepository.startRecording()
                if (result.isFailure) {
                    val exception = result.exceptionOrNull()
                    val error = exception?.message ?: "Failed to start recording"
                    Log.e(TAG, "Recording start failed: $error", exception)
                    TraceLogger.error("RecordingViewModel", "Recording start failed: $error", exception)

                    // Make error message more user-friendly
                    val userMessage = when {
                        error.contains("permission", ignoreCase = true) ||
                        error.contains("RECORD_AUDIO", ignoreCase = true) ->
                            "Microphone permission not granted. Please enable microphone access in Android Settings."
                        error.contains("AudioRecord", ignoreCase = true) ->
                            "Cannot access microphone. It may be in use by another app."
                        else -> "Failed to start recording: $error"
                    }

                    _errorMessage.value = userMessage
                    _recordingState.value = RecordingState.ERROR
                } else {
                    TraceLogger.trace("RecordingViewModel", "Recording started successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
                TraceLogger.error("RecordingViewModel", "Exception starting recording", e)
                _errorMessage.value = "Error: ${e.message ?: "Unknown error occurred"}"
                _recordingState.value = RecordingState.ERROR
            }
        }
    }

    /**
     * Stop recording and return audio file for immediate processing.
     */
    suspend fun stopRecording(): StopRecordingResult? {
        return try {
            Log.d(TAG, "Stopping recording...")
            TraceLogger.trace("RecordingViewModel", "User stopped recording")

            // Capture final duration before repository resets timer
            _finalRecordingDuration.value = recordingDuration.value

            // Stop recording and get file
            val recordingResult = voiceRepository.stopRecording()
            if (recordingResult.isFailure) {
                val exception = recordingResult.exceptionOrNull()
                val error = exception?.message ?: "Failed to stop recording"
                Log.e(TAG, "Recording stop failed: $error", exception)
                TraceLogger.error("RecordingViewModel", "Recording stop failed", exception)
                _errorMessage.value = "Failed to stop recording: $error"
                _recordingState.value = RecordingState.ERROR
                return null
            }
            TraceLogger.trace("RecordingViewModel", "Recording stopped successfully")

            val audioFile = recordingResult.getOrNull()
            if (audioFile == null) {
                _errorMessage.value = "Audio file is null"
                _recordingState.value = RecordingState.ERROR
                return null
            }

            _needsConfirmation.value = false
            _recordingState.value = RecordingState.IDLE

            Log.d(TAG, "Audio file ready: ${audioFile.name}, size=${audioFile.length()} bytes, duration=${_finalRecordingDuration.value}ms")
            _recordedAudioFile.value = audioFile

            StopRecordingResult(audioFile = audioFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            _errorMessage.value = e.message
            _recordingState.value = RecordingState.ERROR
            null
        }
    }

    /**
     * User confirmed processing the recording
     */
    fun confirmRecording() {
        Log.d(TAG, "Recording confirmed by user")
        _needsConfirmation.value = false
        _recordingState.value = RecordingState.IDLE
    }

    /**
     * User rejected the recording - discard it
     */
    fun rejectRecording() {
        viewModelScope.launch {
            Log.d(TAG, "Recording rejected by user - discarding audio")
            _needsConfirmation.value = false
            _recordedAudioFile.value?.delete()
            _recordedAudioFile.value = null
            _recordingState.value = RecordingState.IDLE
            _finalRecordingDuration.value = 0L
        }
    }

    /**
     * Cancel recording - shows confirmation for long recordings
     */
    fun cancelRecording() {
        viewModelScope.launch {
            try {
                val duration = recordingDuration.value
                Log.d(TAG, "Cancel recording requested - duration: ${duration}ms")

                // If recording is longer than 30 seconds, show confirmation
                if (duration >= 30000) {
                    Log.d(TAG, "Long recording (${duration}ms) - showing confirmation")
                    _showCancelConfirmation.value = true
                } else {
                    // Short recording - cancel immediately
                    Log.d(TAG, "Short recording - canceling immediately")
                    performCancelRecording()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in cancelRecording", e)
                _errorMessage.value = e.message
                _recordingState.value = RecordingState.ERROR
            }
        }
    }

    /**
     * User confirmed they want to cancel - actually cancel the recording
     */
    fun confirmCancelRecording() {
        viewModelScope.launch {
            Log.d(TAG, "User confirmed cancellation")
            _showCancelConfirmation.value = false
            performCancelRecording()
        }
    }

    /**
     * User dismissed the cancel confirmation - keep recording
     */
    fun dismissCancelConfirmation() {
        Log.d(TAG, "User dismissed cancel confirmation - continuing recording")
        _showCancelConfirmation.value = false
    }

    /**
     * Actually perform the recording cancellation
     */
    private suspend fun performCancelRecording() {
        try {
            Log.d(TAG, "Performing recording cancellation...")
            voiceRepository.cancelRecording()
            _recordingState.value = RecordingState.IDLE
            _errorMessage.value = null
            _recordedAudioFile.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling recording", e)
            _errorMessage.value = e.message
            _recordingState.value = RecordingState.ERROR
        }
    }

    /**
     * Set recording state to processing (called when transcription starts)
     */
    fun setProcessing() {
        _recordingState.value = RecordingState.PROCESSING
    }

    /**
     * Set recording state to idle (called when transcription completes)
     */
    fun setIdle() {
        _recordingState.value = RecordingState.IDLE
        _recordedAudioFile.value = null
    }

    /**
     * Set recording state to error (called when transcription fails)
     */
    fun setError(message: String) {
        _errorMessage.value = message
        _recordingState.value = RecordingState.ERROR
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
        if (_recordingState.value == RecordingState.ERROR) {
            _recordingState.value = RecordingState.IDLE
        }
    }

    /**
     * Clear recorded audio file
     */
    fun clearRecordedAudioFile() {
        _recordedAudioFile.value = null
    }

    /**
     * Enable walkie-talkie mode
     */
    fun enableWalkieTalkieMode(message: String) {
        Log.d(TAG, "Enabling walkie-talkie mode")
        TraceLogger.trace("RecordingViewModel", "Walkie-talkie mode enabled")
        _walkieTalkieMode.value = true
        _modeChangeMessage.value = message
    }

    /**
     * Disable walkie-talkie mode
     */
    fun disableWalkieTalkieMode(message: String) {
        Log.d(TAG, "Disabling walkie-talkie mode")
        TraceLogger.trace("RecordingViewModel", "Walkie-talkie mode disabled")
        _walkieTalkieMode.value = false
        _modeChangeMessage.value = message
    }

    /**
     * Clear mode change message
     */
    fun clearModeChangeMessage() {
        _modeChangeMessage.value = null
    }

    /**
     * Set flag that recording was cut due to timeout
     */
    fun setRecordingWasCut() {
        _recordingWasCut.value = true
    }

    /**
     * Clear the recording was cut flag
     */
    fun clearRecordingWasCutFlag() {
        _recordingWasCut.value = false
    }

    /**
     * Synchronize view-model state with repository recorder state when IME view is recreated.
     * This prevents UI state desync when keyboard is hidden/shown or window changes.
     */
    fun syncRecordingState() {
        Log.d(TAG, "Syncing recording state - current UI state: ${_recordingState.value}")

        // Check actual repository recording state
        val isActuallyRecording = voiceRepository.isRecording()
        Log.d(TAG, "Repository isRecording: $isActuallyRecording")

        if (isActuallyRecording) {
            // Repository says we're recording - update UI state
            if (_recordingState.value != RecordingState.RECORDING) {
                Log.d(TAG, "Syncing state to RECORDING")
                _recordingState.value = RecordingState.RECORDING
            }
        } else {
            // Not recording in repository
            // If UI thinks we're recording, reset to IDLE
            if (_recordingState.value == RecordingState.RECORDING) {
                Log.d(TAG, "Repository not recording but UI shows RECORDING - resetting to IDLE")
                _recordingState.value = RecordingState.IDLE
            }
            // Keep PROCESSING state if that's what we're in
            // It will be cleared when transcription completes
        }
    }
}
