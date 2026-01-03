package com.hyperwhisper.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyperwhisper.data.*
import com.hyperwhisper.network.VoiceRepository
import com.hyperwhisper.utils.TraceLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class KeyboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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

    private val _transcriptionProgress = MutableStateFlow<Float?>(null)
    val transcriptionProgress: StateFlow<Float?> = _transcriptionProgress.asStateFlow()

    // Pending configuration command for confirmation dialog
    private val _pendingCommandResult = MutableStateFlow<VoiceCommandResult?>(null)
    val pendingCommandResult: StateFlow<VoiceCommandResult?> = _pendingCommandResult.asStateFlow()

    // Job for current transcription (to allow cancellation)
    private var transcriptionJob: kotlinx.coroutines.Job? = null

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

    // Usage statistics (cumulative audio duration, etc.)
    val usageStatistics: StateFlow<UsageStatistics> = settingsRepository.usageStatistics
        .stateIn(viewModelScope, SharingStarted.Eagerly, UsageStatistics())

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

                // Validate settings - API key only required for cloud providers or LOCAL with second-stage
                val needsApiKey = when {
                    // LOCAL mode without second-stage doesn't need API key
                    settings.provider == ApiProvider.LOCAL &&
                    !settings.localSettings.enableSecondStageProcessing -> false

                    // LOCAL mode with second-stage needs API key for second-stage provider
                    settings.provider == ApiProvider.LOCAL &&
                    settings.localSettings.enableSecondStageProcessing -> {
                        settings.apiKeys[settings.localSettings.secondStageProvider].isNullOrBlank()
                    }

                    // All cloud providers need API key
                    else -> settings.getCurrentApiKey().isBlank()
                }

                if (needsApiKey) {
                    val providerName = if (settings.provider == ApiProvider.LOCAL) {
                        settings.localSettings.secondStageProvider.displayName
                    } else {
                        settings.provider.displayName
                    }
                    _errorMessage.value = "Please configure API key for $providerName in settings"
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

                // Save audio file to persistent storage
                val savedAudioPath = saveAudioFileToPersistentStorage(audioFile)
                Log.d(TAG, "Audio file saved to: $savedAudioPath")

                // Start transcription with progress tracking and cancellation support
                transcriptionJob = viewModelScope.launch {
                    try {
                        // Start progress simulation
                        _transcriptionProgress.value = 0.1f

                        // Launch progress updater
                        val progressJob = launch {
                            var progress = 0.1f
                            while (progress < 0.9f) {
                                kotlinx.coroutines.delay(300)
                                progress += 0.05f
                                _transcriptionProgress.value = progress
                            }
                        }

                        // Process audio through API
                        val result = voiceRepository.processAudio(audioFile, mode, settings)

                        // Cancel progress updater
                        progressJob.cancel()
                        _transcriptionProgress.value = 1.0f

                        when (result) {
                    is ApiResult.Success -> {
                        Log.d(TAG, "Transcription successful: ${result.data}")
                        TraceLogger.trace("KeyboardViewModel", "Transcription successful, length: ${result.data.length} chars")

                        // Check if in configuration mode
                        if (mode.id == "configuration") {
                            // Process as configuration command
                            viewModelScope.launch {
                                try {
                                    val commandResult = voiceCommandProcessor.executeCommand(
                                        result.data,
                                        viewModelScope
                                    )

                                    if (commandResult.success) {
                                        // Show pending command for user confirmation
                                        _pendingCommandResult.value = commandResult
                                        Log.d(TAG, "Configuration command pending: ${commandResult.message}")
                                        TraceLogger.trace("KeyboardViewModel", "Configuration pending: ${commandResult.message}")
                                    } else {
                                        // Show error directly
                                        _errorMessage.value = commandResult.message
                                    }

                                    // Don't commit command text to input field
                                    _transcribedText.value = ""
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error processing configuration command", e)
                                    TraceLogger.error("KeyboardViewModel", "Configuration command error", e)
                                    _errorMessage.value = "Configuration error: ${e.message}"
                                }
                            }
                        } else {
                            // Normal transcription mode
                            _transcribedText.value = result.data

                            // Save to history with audio file path
                            settingsRepository.addToHistory(result.data, savedAudioPath)
                        }

                        _processingInfo.value = result.processingInfo
                        _recordingState.value = RecordingState.IDLE
                        _transcriptionProgress.value = null
                    }
                    is ApiResult.Error -> {
                        Log.e(TAG, "Transcription failed: ${result.message}")
                        TraceLogger.error("KeyboardViewModel", "Transcription failed: ${result.message}")
                        _errorMessage.value = "API Error: ${result.message}"
                        _recordingState.value = RecordingState.ERROR
                        _transcriptionProgress.value = null
                    }
                    is ApiResult.Loading -> {
                        // Should not happen in this flow
                    }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        Log.d(TAG, "Transcription cancelled by user")
                        TraceLogger.trace("KeyboardViewModel", "Transcription cancelled")
                        _recordingState.value = RecordingState.IDLE
                        _transcriptionProgress.value = null
                        throw e // Re-throw to properly cancel the coroutine
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during transcription", e)
                        TraceLogger.error("KeyboardViewModel", "Transcription error", e)
                        _errorMessage.value = e.message
                        _recordingState.value = RecordingState.ERROR
                        _transcriptionProgress.value = null
                    } finally {
                        // Cleanup audio file
                        audioFile.delete()
                        transcriptionJob = null
                    }
                }

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
     * Cancel ongoing transcription
     */
    fun cancelTranscription() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Canceling transcription...")
                TraceLogger.trace("KeyboardViewModel", "User cancelled transcription")

                transcriptionJob?.cancel()
                transcriptionJob = null

                _recordingState.value = RecordingState.IDLE
                _transcriptionProgress.value = null
                _errorMessage.value = null

                Log.d(TAG, "Transcription cancelled successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error canceling transcription", e)
                TraceLogger.error("KeyboardViewModel", "Error cancelling transcription", e)
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

    /**
     * Reprocess audio from history with current settings
     */
    fun reprocessWithCurrentSettings(item: TranscriptionHistoryItem) {
        viewModelScope.launch {
            try {
                val audioFilePath = item.audioFilePath
                if (audioFilePath == null) {
                    _errorMessage.value = "No audio file available for reprocessing"
                    return@launch
                }

                val audioFile = File(audioFilePath)
                if (!audioFile.exists()) {
                    _errorMessage.value = "Audio file no longer exists"
                    return@launch
                }

                Log.d(TAG, "Reprocessing audio with current settings: ${audioFile.name}")
                TraceLogger.trace("KeyboardViewModel", "Reprocessing audio: ${audioFile.name}")
                _recordingState.value = RecordingState.PROCESSING

                // Get current settings and mode
                val settings = apiSettings.value
                val mode = selectedMode.value

                // Validate settings - API key only required for cloud providers or LOCAL with second-stage
                val needsApiKey = when {
                    // LOCAL mode without second-stage doesn't need API key
                    settings.provider == ApiProvider.LOCAL &&
                    !settings.localSettings.enableSecondStageProcessing -> false

                    // LOCAL mode with second-stage needs API key for second-stage provider
                    settings.provider == ApiProvider.LOCAL &&
                    settings.localSettings.enableSecondStageProcessing -> {
                        settings.apiKeys[settings.localSettings.secondStageProvider].isNullOrBlank()
                    }

                    // All cloud providers need API key
                    else -> settings.getCurrentApiKey().isBlank()
                }

                if (needsApiKey) {
                    val providerName = if (settings.provider == ApiProvider.LOCAL) {
                        settings.localSettings.secondStageProvider.displayName
                    } else {
                        settings.provider.displayName
                    }
                    _errorMessage.value = "Please configure API key for $providerName in settings"
                    _recordingState.value = RecordingState.ERROR
                    return@launch
                }

                if (mode == null) {
                    _errorMessage.value = "No voice mode selected"
                    _recordingState.value = RecordingState.ERROR
                    return@launch
                }

                // Process audio through API
                when (val result = voiceRepository.processAudio(audioFile, mode, settings)) {
                    is ApiResult.Success -> {
                        Log.d(TAG, "Reprocessing successful: ${result.data}")
                        TraceLogger.trace("KeyboardViewModel", "Reprocessing successful, length: ${result.data.length} chars")

                        _transcribedText.value = result.data
                        _processingInfo.value = result.processingInfo
                        _recordingState.value = RecordingState.IDLE

                        // Update history with new transcription
                        settingsRepository.addToHistory(result.data, audioFilePath)
                    }
                    is ApiResult.Error -> {
                        Log.e(TAG, "Reprocessing failed: ${result.message}")
                        TraceLogger.error("KeyboardViewModel", "Reprocessing failed: ${result.message}")
                        _errorMessage.value = "API Error: ${result.message}"
                        _recordingState.value = RecordingState.ERROR
                    }
                    is ApiResult.Loading -> {
                        // Should not happen in this flow
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reprocessing audio", e)
                TraceLogger.error("KeyboardViewModel", "Error reprocessing audio", e)
                _errorMessage.value = e.message
                _recordingState.value = RecordingState.ERROR
            }
        }
    }

    /**
     * Reprocess audio from history with new settings
     */
    fun reprocessWithNewSettings(item: TranscriptionHistoryItem, newSettings: ApiSettings, newMode: VoiceMode) {
        viewModelScope.launch {
            try {
                val audioFilePath = item.audioFilePath
                if (audioFilePath == null) {
                    _errorMessage.value = "No audio file available for reprocessing"
                    return@launch
                }

                val audioFile = File(audioFilePath)
                if (!audioFile.exists()) {
                    _errorMessage.value = "Audio file no longer exists"
                    return@launch
                }

                Log.d(TAG, "Reprocessing audio with new settings: ${audioFile.name}, mode: ${newMode.name}")
                TraceLogger.trace("KeyboardViewModel", "Reprocessing with new settings: ${newMode.name}, provider: ${newSettings.provider}")
                _recordingState.value = RecordingState.PROCESSING

                // Validate settings - API key only required for cloud providers or LOCAL with second-stage
                val needsApiKey = when {
                    // LOCAL mode without second-stage doesn't need API key
                    newSettings.provider == ApiProvider.LOCAL &&
                    !newSettings.localSettings.enableSecondStageProcessing -> false

                    // LOCAL mode with second-stage needs API key for second-stage provider
                    newSettings.provider == ApiProvider.LOCAL &&
                    newSettings.localSettings.enableSecondStageProcessing -> {
                        newSettings.apiKeys[newSettings.localSettings.secondStageProvider].isNullOrBlank()
                    }

                    // All cloud providers need API key
                    else -> newSettings.getCurrentApiKey().isBlank()
                }

                if (needsApiKey) {
                    val providerName = if (newSettings.provider == ApiProvider.LOCAL) {
                        newSettings.localSettings.secondStageProvider.displayName
                    } else {
                        newSettings.provider.displayName
                    }
                    _errorMessage.value = "Please configure API key for $providerName"
                    _recordingState.value = RecordingState.ERROR
                    return@launch
                }

                // Process audio through API
                when (val result = voiceRepository.processAudio(audioFile, newMode, newSettings)) {
                    is ApiResult.Success -> {
                        Log.d(TAG, "Reprocessing with new settings successful: ${result.data}")
                        TraceLogger.trace("KeyboardViewModel", "Reprocessing successful, length: ${result.data.length} chars")

                        _transcribedText.value = result.data
                        _processingInfo.value = result.processingInfo
                        _recordingState.value = RecordingState.IDLE

                        // Update history with new transcription
                        settingsRepository.addToHistory(result.data, audioFilePath)
                    }
                    is ApiResult.Error -> {
                        Log.e(TAG, "Reprocessing failed: ${result.message}")
                        TraceLogger.error("KeyboardViewModel", "Reprocessing failed: ${result.message}")
                        _errorMessage.value = "API Error: ${result.message}"
                        _recordingState.value = RecordingState.ERROR
                    }
                    is ApiResult.Loading -> {
                        // Should not happen in this flow
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reprocessing audio with new settings", e)
                TraceLogger.error("KeyboardViewModel", "Error reprocessing with new settings", e)
                _errorMessage.value = e.message
                _recordingState.value = RecordingState.ERROR
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
            TraceLogger.trace("KeyboardViewModel", "Audio file saved: ${destFile.name}, size: ${destFile.length()} bytes")

            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save audio file to persistent storage", e)
            TraceLogger.error("KeyboardViewModel", "Failed to save audio file", e)
            null
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
