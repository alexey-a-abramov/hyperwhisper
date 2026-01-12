package com.hyperwhisper.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyperwhisper.data.RecordingState
import com.hyperwhisper.network.VoiceRepository
import com.hyperwhisper.utils.TraceLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for managing audio recording state
 * Handles recording lifecycle and timeout monitoring
 */
@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val voiceRepository: VoiceRepository
) : ViewModel() {

    companion object {
        private const val TAG = "RecordingViewModel"
        private const val MAX_RECORDING_DURATION_MS = 180000L // 3 minutes
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

    /**
     * Start recording audio
     */
    fun startRecording() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting recording...")
                TraceLogger.trace("RecordingViewModel", "User tapped mic - starting recording")
                _recordingState.value = RecordingState.RECORDING
                _errorMessage.value = null
                _recordedAudioFile.value = null

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
     * Stop recording and return audio file
     */
    fun stopRecording() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Stopping recording...")
                TraceLogger.trace("RecordingViewModel", "User stopped recording")

                // Stop recording and get file
                val recordingResult = voiceRepository.stopRecording()
                if (recordingResult.isFailure) {
                    val exception = recordingResult.exceptionOrNull()
                    val error = exception?.message ?: "Failed to stop recording"
                    Log.e(TAG, "Recording stop failed: $error", exception)
                    TraceLogger.error("RecordingViewModel", "Recording stop failed", exception)
                    _errorMessage.value = "Failed to stop recording: $error"
                    _recordingState.value = RecordingState.ERROR
                    return@launch
                }
                TraceLogger.trace("RecordingViewModel", "Recording stopped successfully")

                val audioFile = recordingResult.getOrNull()
                if (audioFile == null) {
                    _errorMessage.value = "Audio file is null"
                    _recordingState.value = RecordingState.ERROR
                    return@launch
                }

                Log.d(TAG, "Audio file ready: ${audioFile.name}, size=${audioFile.length()} bytes")
                _recordedAudioFile.value = audioFile
                _recordingState.value = RecordingState.IDLE

            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording", e)
                _errorMessage.value = e.message
                _recordingState.value = RecordingState.ERROR
            }
        }
    }

    /**
     * Cancel recording
     */
    fun cancelRecording() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Canceling recording...")
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

    init {
        // Monitor recording duration for timeout
        viewModelScope.launch {
            recordingDuration.collect { duration ->
                if (duration >= MAX_RECORDING_DURATION_MS && recordingState.value == RecordingState.RECORDING) {
                    Log.d(TAG, "Max recording duration reached, auto-stopping")
                    stopRecording()
                }
            }
        }
    }
}
