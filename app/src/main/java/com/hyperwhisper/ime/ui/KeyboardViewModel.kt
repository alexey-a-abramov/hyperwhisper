package com.hyperwhisper.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyperwhisper.data.*
import com.hyperwhisper.network.VoiceRepository
import com.hyperwhisper.utils.TraceLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeyboardViewModel @Inject constructor(
    private val voiceRepository: VoiceRepository,
    private val settingsRepository: SettingsRepository,
    private val voiceCommandProcessor: com.hyperwhisper.data.VoiceCommandProcessor
) : ViewModel() {

    companion object {
        private const val TAG = "KeyboardViewModel"
        private const val MAX_RECORDING_DURATION_MS = 180000L // 3 minutes
    }

    // State flows
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _processingInfo = MutableStateFlow<ProcessingInfo?>(null)
    val processingInfo: StateFlow<ProcessingInfo?> = _processingInfo.asStateFlow()

    // Recording duration from repository
    val recordingDuration: StateFlow<Long> = voiceRepository.getRecordingDuration()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Transcription history
    val transcriptionHistory: StateFlow<List<TranscriptionHistoryItem>> = settingsRepository.transcriptionHistory
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Settings and modes from repository
    val voiceModes: StateFlow<List<VoiceMode>> = settingsRepository.voiceModes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedModeId: StateFlow<String> = settingsRepository.selectedMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "verbatim")

    val apiSettings: StateFlow<ApiSettings> = settingsRepository.apiSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ApiSettings())

    val appearanceSettings: StateFlow<AppearanceSettings> = settingsRepository.appearanceSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceSettings())

    // Recently used languages
    val recentlyUsedLanguages: StateFlow<List<String>> = settingsRepository.recentlyUsedLanguages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Derived state for selected mode
    val selectedMode: StateFlow<VoiceMode?> = combine(
        voiceModes,
        selectedModeId
    ) { modes, selectedId ->
        modes.firstOrNull { it.id == selectedId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Start recording audio and monitor for timeout
     */
    fun startRecording() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting recording...")
                TraceLogger.trace("KeyboardViewModel", "User tapped mic - starting recording")
                _recordingState.value = RecordingState.RECORDING
                _errorMessage.value = null

                val result = voiceRepository.startRecording()
                if (result.isFailure) {
                    val exception = result.exceptionOrNull()
                    val error = exception?.message ?: "Failed to start recording"
                    Log.e(TAG, "Recording start failed: $error", exception)
                    TraceLogger.error("KeyboardViewModel", "Recording start failed: $error", exception)

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
                    TraceLogger.trace("KeyboardViewModel", "Recording started successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
                TraceLogger.error("KeyboardViewModel", "Exception starting recording", e)
                _errorMessage.value = "Error: ${e.message ?: "Unknown error occurred"}"
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
                TraceLogger.trace("KeyboardViewModel", "User stopped recording - processing audio")
                _recordingState.value = RecordingState.PROCESSING

                // Stop recording and get file
                val recordingResult = voiceRepository.stopRecording()
                if (recordingResult.isFailure) {
                    val exception = recordingResult.exceptionOrNull()
                    val error = exception?.message ?: "Failed to stop recording"
                    Log.e(TAG, "Recording stop failed: $error", exception)
                    TraceLogger.error("KeyboardViewModel", "Recording stop failed", exception)
                    _errorMessage.value = "Failed to stop recording: $error"
                    _recordingState.value = RecordingState.ERROR
                    return@launch
                }
                TraceLogger.trace("KeyboardViewModel", "Recording stopped successfully")

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
                if (settings.getCurrentApiKey().isBlank()) {
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
                TraceLogger.trace("KeyboardViewModel", "Processing audio with mode: ${mode.name}, provider: ${settings.provider}")

                // Process audio through API
                when (val result = voiceRepository.processAudio(audioFile, mode, settings)) {
                    is ApiResult.Success -> {
                        Log.d(TAG, "Transcription successful: ${result.data}")
                        TraceLogger.trace("KeyboardViewModel", "Transcription successful, length: ${result.data.length} chars")

                        // Check if in voice commands mode
                        if (mode.id == "voice_commands") {
                            // Process as voice command
                            viewModelScope.launch {
                                try {
                                    val commandResult = voiceCommandProcessor.executeCommand(
                                        result.data,
                                        viewModelScope
                                    )

                                    // Show notification
                                    voiceCommandProcessor.showNotification(commandResult)

                                    // Don't commit command text to input field
                                    // Just log it
                                    Log.d(TAG, "Voice command executed: ${commandResult.message}")
                                    TraceLogger.trace("KeyboardViewModel", "Voice command: ${commandResult.message}")

                                    // Clear transcribed text so it doesn't get committed
                                    _transcribedText.value = ""
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error processing voice command", e)
                                    TraceLogger.error("KeyboardViewModel", "Voice command error", e)
                                    _errorMessage.value = "Voice command error: ${e.message}"
                                }
                            }
                        } else {
                            // Normal transcription mode
                            _transcribedText.value = result.data

                            // Save to history
                            settingsRepository.addToHistory(result.data)
                        }

                        _processingInfo.value = result.processingInfo
                        _recordingState.value = RecordingState.IDLE
                    }
                    is ApiResult.Error -> {
                        Log.e(TAG, "Transcription failed: ${result.message}")
                        TraceLogger.error("KeyboardViewModel", "Transcription failed: ${result.message}")
                        _errorMessage.value = "API Error: ${result.message}"
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
     * Clear processing info
     */
    fun clearProcessingInfo() {
        _processingInfo.value = null
    }

    /**
     * Set input language hint for quick switching from keyboard
     */
    fun setInputLanguage(languageCode: String) {
        viewModelScope.launch {
            val currentSettings = apiSettings.value
            val updatedSettings = currentSettings.copy(inputLanguage = languageCode)
            settingsRepository.saveApiSettings(updatedSettings)
            // Track language usage
            settingsRepository.trackLanguageUsage(languageCode)
            Log.d(TAG, "Input language hint changed to: ${if (languageCode.isEmpty()) "Auto" else languageCode}")
        }
    }

    /**
     * Set output language for quick switching from keyboard
     */
    fun setOutputLanguage(languageCode: String) {
        viewModelScope.launch {
            val currentSettings = apiSettings.value
            val updatedSettings = currentSettings.copy(outputLanguage = languageCode)
            settingsRepository.saveApiSettings(updatedSettings)
            // Track language usage
            settingsRepository.trackLanguageUsage(languageCode)
            Log.d(TAG, "Output language changed to: ${if (languageCode.isEmpty()) "Auto" else languageCode}")
        }
    }

    /**
     * Clear transcription history
     */
    fun clearHistory() {
        viewModelScope.launch {
            settingsRepository.clearHistory()
        }
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

    /**
     * Reset state
     */
    fun reset() {
        _recordingState.value = RecordingState.IDLE
        _transcribedText.value = ""
        _errorMessage.value = null
    }
}
