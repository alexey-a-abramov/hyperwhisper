package com.hyperwhisper.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyperwhisper.data.*
import com.hyperwhisper.ui.viewmodels.HistoryViewModel
import com.hyperwhisper.ui.viewmodels.RecordingViewModel
import com.hyperwhisper.ui.viewmodels.TranscriptionViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Coordinator ViewModel for the Keyboard screen
 * Orchestrates recording, transcription, and history management
 * Delegates to specialized view models for specific responsibilities
 */
@HiltViewModel
class KeyboardViewModel @Inject constructor(
    private val recordingViewModel: RecordingViewModel,
    private val transcriptionViewModel: TranscriptionViewModel,
    private val historyViewModel: HistoryViewModel,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "KeyboardViewModel"
    }

    // ============================================================================
    // Recording State - Delegated to RecordingViewModel
    // ============================================================================

    val recordingState: StateFlow<RecordingState> = recordingViewModel.recordingState
    val recordingDuration: StateFlow<Long> = recordingViewModel.recordingDuration

    // ============================================================================
    // Transcription State - Delegated to TranscriptionViewModel
    // ============================================================================

    val transcribedText: StateFlow<String> = transcriptionViewModel.transcribedText
    val processingInfo: StateFlow<ProcessingInfo?> = transcriptionViewModel.processingInfo
    val transcriptionProgress: StateFlow<Float?> = transcriptionViewModel.transcriptionProgress
    val processingStage: StateFlow<ProcessingStage?> = transcriptionViewModel.processingStage
    val pendingCommandResult: StateFlow<VoiceCommandResult?> = transcriptionViewModel.pendingCommandResult
    val lastAudioFileSize: StateFlow<Long> = transcriptionViewModel.lastAudioFileSize
    val lastAudioDuration: StateFlow<Double> = transcriptionViewModel.lastAudioDuration

    // ============================================================================
    // History State - Delegated to HistoryViewModel
    // ============================================================================

    val transcriptionHistory: StateFlow<List<TranscriptionHistoryItem>> = historyViewModel.transcriptionHistory

    // ============================================================================
    // Settings State - From SettingsRepository
    // ============================================================================

    val voiceModes: StateFlow<List<VoiceMode>> = settingsRepository.voiceModes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedModeId: StateFlow<String> = settingsRepository.selectedMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "verbatim")

    val apiSettings: StateFlow<ApiSettings> = settingsRepository.apiSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ApiSettings())

    val appearanceSettings: StateFlow<AppearanceSettings> = settingsRepository.appearanceSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceSettings())

    val recentlyUsedLanguages: StateFlow<List<String>> = settingsRepository.recentlyUsedLanguages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val usageStatistics: StateFlow<UsageStatistics> = settingsRepository.usageStatistics
        .stateIn(viewModelScope, SharingStarted.Eagerly, UsageStatistics())

    // Derived state for selected mode
    val selectedMode: StateFlow<VoiceMode?> = combine(
        voiceModes,
        selectedModeId
    ) { modes, selectedId ->
        modes.firstOrNull { it.id == selectedId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ============================================================================
    // Combined Error State
    // ============================================================================

    val errorMessage: StateFlow<String?> = combine(
        recordingViewModel.errorMessage,
        transcriptionViewModel.errorMessage,
        historyViewModel.errorMessage
    ) { recordingError, transcriptionError, historyError ->
        recordingError ?: transcriptionError ?: historyError
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ============================================================================
    // Recording Operations
    // ============================================================================

    /**
     * Start recording audio
     */
    fun startRecording() {
        recordingViewModel.startRecording()
    }

    /**
     * Stop recording and process audio
     */
    fun stopRecording() {
        viewModelScope.launch {
            // Stop recording
            recordingViewModel.stopRecording()

            // Wait for audio file
            recordingViewModel.recordedAudioFile
                .filterNotNull()
                .take(1)
                .collect { audioFile ->
                    // Set recording state to processing
                    recordingViewModel.setProcessing()

                    // Get current settings and mode
                    val settings = apiSettings.value
                    val mode = selectedMode.value

                    if (mode == null) {
                        recordingViewModel.setError("No voice mode selected")
                        return@collect
                    }

                    // Process through transcription view model
                    val savedAudioPath = transcriptionViewModel.processAudio(audioFile, mode, settings)

                    // Handle results based on transcription state
                    launch {
                        // Monitor for transcription completion or error
                        combine(
                            transcriptionViewModel.transcribedText,
                            transcriptionViewModel.errorMessage
                        ) { text, error ->
                            when {
                                error != null -> {
                                    // Error occurred
                                    recordingViewModel.setError(error)
                                    // Save audio to history even on error
                                    if (savedAudioPath != null) {
                                        historyViewModel.addToHistory("", savedAudioPath)
                                    }
                                }
                                text.isNotEmpty() -> {
                                    // Success
                                    recordingViewModel.setIdle()
                                    // Save to history with audio file path
                                    if (savedAudioPath != null) {
                                        historyViewModel.addToHistory(text, savedAudioPath)
                                    }
                                }
                            }
                        }.take(1).collect()
                    }

                    // Cleanup audio file
                    audioFile.delete()
                    recordingViewModel.clearRecordedAudioFile()
                }
        }
    }

    /**
     * Cancel recording
     */
    fun cancelRecording() {
        recordingViewModel.cancelRecording()
    }

    /**
     * Cancel ongoing transcription
     */
    fun cancelTranscription() {
        transcriptionViewModel.cancelTranscription()
        recordingViewModel.setIdle()
    }

    // ============================================================================
    // Settings Operations
    // ============================================================================

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
            }
        }
    }

    /**
     * Set input language
     */
    fun setInputLanguage(languageCode: String) {
        viewModelScope.launch {
            val currentSettings = apiSettings.value
            val updatedSettings = currentSettings.copy(inputLanguage = languageCode)
            settingsRepository.saveApiSettings(updatedSettings)
            // Track language usage
            settingsRepository.trackLanguageUsage(languageCode)
            Log.d(TAG, "Input language changed to: ${if (languageCode.isEmpty()) "Auto" else languageCode}")
        }
    }

    /**
     * Set output language
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

    // ============================================================================
    // History Operations
    // ============================================================================

    /**
     * Clear transcription history
     */
    fun clearHistory() {
        historyViewModel.clearHistory()
    }

    /**
     * Reprocess audio from history with current settings
     */
    fun reprocessWithCurrentSettings(item: TranscriptionHistoryItem) {
        viewModelScope.launch {
            recordingViewModel.setProcessing()
            val settings = apiSettings.value
            val mode = selectedMode.value
            historyViewModel.reprocessWithCurrentSettings(item, settings, mode)

            // Monitor reprocessing result
            historyViewModel.reprocessedText
                .filterNotNull()
                .take(1)
                .collect { text ->
                    // Update transcribed text for UI
                    // Note: The history is already updated by HistoryViewModel
                    recordingViewModel.setIdle()
                    historyViewModel.clearReprocessedText()
                }
        }
    }

    /**
     * Reprocess audio from history with new settings
     */
    fun reprocessWithNewSettings(
        item: TranscriptionHistoryItem,
        newSettings: ApiSettings,
        newMode: VoiceMode
    ) {
        viewModelScope.launch {
            recordingViewModel.setProcessing()
            historyViewModel.reprocessWithNewSettings(item, newSettings, newMode)

            // Monitor reprocessing result
            historyViewModel.reprocessedText
                .filterNotNull()
                .take(1)
                .collect { text ->
                    // Update transcribed text for UI
                    // Note: The history is already updated by HistoryViewModel
                    recordingViewModel.setIdle()
                    historyViewModel.clearReprocessedText()
                }
        }
    }

    // ============================================================================
    // Utility Operations
    // ============================================================================

    /**
     * Clear error message
     */
    fun clearError() {
        recordingViewModel.clearError()
        transcriptionViewModel.clearError()
        historyViewModel.clearError()
    }

    /**
     * Clear transcribed text
     */
    fun clearTranscribedText() {
        transcriptionViewModel.clearTranscribedText()
    }

    /**
     * Clear processing info
     */
    fun clearProcessingInfo() {
        transcriptionViewModel.clearProcessingInfo()
    }

    /**
     * Confirm and apply pending configuration command
     */
    fun confirmPendingCommand() {
        transcriptionViewModel.confirmPendingCommand()
    }

    /**
     * Reject pending configuration command
     */
    fun rejectPendingCommand() {
        transcriptionViewModel.rejectPendingCommand()
    }

    /**
     * Reset state
     */
    fun reset() {
        recordingViewModel.clearError()
        transcriptionViewModel.clearTranscribedText()
        transcriptionViewModel.clearError()
    }
}
