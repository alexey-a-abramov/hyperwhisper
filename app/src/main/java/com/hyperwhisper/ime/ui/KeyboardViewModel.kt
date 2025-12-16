package com.hyperwhisper.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyperwhisper.data.*
import com.hyperwhisper.network.VoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeyboardViewModel @Inject constructor(
    private val voiceRepository: VoiceRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "KeyboardViewModel"
    }

    // State flows
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Settings and modes from repository
    val voiceModes: StateFlow<List<VoiceMode>> = settingsRepository.voiceModes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedModeId: StateFlow<String> = settingsRepository.selectedMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "verbatim")

    val apiSettings: StateFlow<ApiSettings> = settingsRepository.apiSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ApiSettings())

    // Derived state for selected mode
    val selectedMode: StateFlow<VoiceMode?> = combine(
        voiceModes,
        selectedModeId
    ) { modes, selectedId ->
        modes.firstOrNull { it.id == selectedId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Start recording audio
     */
    fun startRecording() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting recording...")
                _recordingState.value = RecordingState.RECORDING
                _errorMessage.value = null

                val result = voiceRepository.startRecording()
                if (result.isFailure) {
                    val error = result.exceptionOrNull()?.message ?: "Failed to start recording"
                    Log.e(TAG, "Recording start failed: $error")
                    _errorMessage.value = error
                    _recordingState.value = RecordingState.ERROR
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
                _errorMessage.value = e.message
                _recordingState.value = RecordingState.ERROR
            }
        }
    }

    /**
     * Stop recording and process audio
     */
    fun stopRecording() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Stopping recording...")
                _recordingState.value = RecordingState.PROCESSING

                // Stop recording and get file
                val recordingResult = voiceRepository.stopRecording()
                if (recordingResult.isFailure) {
                    val error = recordingResult.exceptionOrNull()?.message ?: "Failed to stop recording"
                    Log.e(TAG, "Recording stop failed: $error")
                    _errorMessage.value = error
                    _recordingState.value = RecordingState.ERROR
                    return@launch
                }

                val audioFile = recordingResult.getOrNull()
                if (audioFile == null) {
                    _errorMessage.value = "Audio file is null"
                    _recordingState.value = RecordingState.ERROR
                    return@launch
                }

                // Get current settings and mode
                val settings = apiSettings.value
                val mode = selectedMode.value

                // Validate settings
                if (settings.apiKey.isBlank()) {
                    _errorMessage.value = "Please configure API key in settings"
                    _recordingState.value = RecordingState.ERROR
                    return@launch
                }

                if (mode == null) {
                    _errorMessage.value = "No voice mode selected"
                    _recordingState.value = RecordingState.ERROR
                    return@launch
                }

                Log.d(TAG, "Processing audio with mode: ${mode.name}")

                // Process audio through API
                when (val result = voiceRepository.processAudio(audioFile, mode, settings)) {
                    is ApiResult.Success -> {
                        Log.d(TAG, "Transcription successful: ${result.data}")
                        _transcribedText.value = result.data
                        _recordingState.value = RecordingState.IDLE
                    }
                    is ApiResult.Error -> {
                        Log.e(TAG, "Transcription failed: ${result.message}")
                        _errorMessage.value = result.message
                        _recordingState.value = RecordingState.ERROR
                    }
                    is ApiResult.Loading -> {
                        // Should not happen in this flow
                    }
                }

                // Cleanup audio file
                audioFile.delete()

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
            } catch (e: Exception) {
                Log.e(TAG, "Error canceling recording", e)
                _errorMessage.value = e.message
                _recordingState.value = RecordingState.ERROR
            }
        }
    }

    /**
     * Select a voice mode
     */
    fun selectMode(modeId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Selecting mode: $modeId")
                settingsRepository.setSelectedMode(modeId)
            } catch (e: Exception) {
                Log.e(TAG, "Error selecting mode", e)
                _errorMessage.value = e.message
            }
        }
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
     * Clear transcribed text
     */
    fun clearTranscribedText() {
        _transcribedText.value = ""
    }

    /**
     * Reset state
     */
    fun reset() {
        _recordingState.value = RecordingState.IDLE
        _transcribedText.value = ""
        _errorMessage.value = null
    }
}
