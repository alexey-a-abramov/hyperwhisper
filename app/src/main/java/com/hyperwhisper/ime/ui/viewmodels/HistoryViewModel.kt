package com.hyperwhisper.ui.viewmodels

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
 * ViewModel for managing transcription history
 * Handles history display, clearing, and reprocessing
 *
 * Note: Not a @HiltViewModel - created internally by KeyboardViewModel
 */
class HistoryViewModel(
    private val voiceRepository: VoiceRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "HistoryViewModel"
    }

    // Transcription history
    val transcriptionHistory: StateFlow<List<TranscriptionHistoryItem>> =
        settingsRepository.transcriptionHistory
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Reprocessing state
    private val _isReprocessing = MutableStateFlow(false)
    val isReprocessing: StateFlow<Boolean> = _isReprocessing.asStateFlow()

    // Reprocessing result
    private val _reprocessedText = MutableStateFlow<String?>(null)
    val reprocessedText: StateFlow<String?> = _reprocessedText.asStateFlow()

    // Processing info from reprocessing
    private val _reprocessingInfo = MutableStateFlow<ProcessingInfo?>(null)
    val reprocessingInfo: StateFlow<ProcessingInfo?> = _reprocessingInfo.asStateFlow()

    // Error message for reprocessing
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Add item to history
     */
    suspend fun addToHistory(text: String, audioFilePath: String?) {
        settingsRepository.addToHistory(text, audioFilePath)
    }

    /**
     * Clear all transcription history
     */
    fun clearHistory() {
        viewModelScope.launch {
            settingsRepository.clearHistory()
            Log.d(TAG, "History cleared")
        }
    }

    /**
     * Reprocess audio from history with current settings
     */
    fun reprocessWithCurrentSettings(
        item: TranscriptionHistoryItem,
        currentSettings: ApiSettings,
        currentMode: VoiceMode?
    ) {
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

                // Skip the cloud-key gate when on-device Whisper is active —
                // local processing doesn't need a cloud key.
                if (!currentSettings.localModelSettings.useLocalWhisper &&
                    currentSettings.getCurrentApiKey().isBlank()) {
                    _errorMessage.value = "Please configure API key for ${currentSettings.provider.displayName} in settings"
                    return@launch
                }

                if (currentMode == null) {
                    _errorMessage.value = "No voice mode selected"
                    return@launch
                }

                Log.d(TAG, "Reprocessing audio with current settings: ${audioFile.name}")
                TraceLogger.trace("HistoryViewModel", "Reprocessing audio: ${audioFile.name}")
                _isReprocessing.value = true
                _errorMessage.value = null

                // Process audio through API
                when (val result = voiceRepository.processAudio(audioFile, currentMode, currentSettings)) {
                    is ApiResult.Success -> {
                        Log.d(TAG, "Reprocessing successful: ${result.data}")
                        TraceLogger.trace("HistoryViewModel", "Reprocessing successful, length: ${result.data.length} chars")

                        _reprocessedText.value = result.data
                        _reprocessingInfo.value = result.processingInfo

                        // Update existing history item instead of creating a new one
                        settingsRepository.updateHistoryItem(item.id, result.data)
                    }
                    is ApiResult.Error -> {
                        Log.e(TAG, "Reprocessing failed: ${result.message}")
                        TraceLogger.error("HistoryViewModel", "Reprocessing failed: ${result.message}")
                        _errorMessage.value = "API Error: ${result.message}"
                    }
                    is ApiResult.Loading -> {
                        // Should not happen in this flow
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reprocessing audio", e)
                TraceLogger.error("HistoryViewModel", "Error reprocessing audio", e)
                _errorMessage.value = e.message
            } finally {
                _isReprocessing.value = false
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

                if (!newSettings.localModelSettings.useLocalWhisper &&
                    newSettings.getCurrentApiKey().isBlank()) {
                    _errorMessage.value = "Please configure API key for ${newSettings.provider.displayName}"
                    return@launch
                }

                Log.d(TAG, "Reprocessing audio with new settings: ${audioFile.name}, mode: ${newMode.name}")
                TraceLogger.trace("HistoryViewModel", "Reprocessing with new settings: ${newMode.name}, provider: ${newSettings.provider}")
                _isReprocessing.value = true
                _errorMessage.value = null

                // Process audio through API
                when (val result = voiceRepository.processAudio(audioFile, newMode, newSettings)) {
                    is ApiResult.Success -> {
                        Log.d(TAG, "Reprocessing with new settings successful: ${result.data}")
                        TraceLogger.trace("HistoryViewModel", "Reprocessing successful, length: ${result.data.length} chars")

                        _reprocessedText.value = result.data
                        _reprocessingInfo.value = result.processingInfo

                        // Update existing history item instead of creating a new one
                        settingsRepository.updateHistoryItem(item.id, result.data)
                    }
                    is ApiResult.Error -> {
                        Log.e(TAG, "Reprocessing failed: ${result.message}")
                        TraceLogger.error("HistoryViewModel", "Reprocessing failed: ${result.message}")
                        _errorMessage.value = "API Error: ${result.message}"
                    }
                    is ApiResult.Loading -> {
                        // Should not happen in this flow
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reprocessing audio with new settings", e)
                TraceLogger.error("HistoryViewModel", "Error reprocessing with new settings", e)
                _errorMessage.value = e.message
            } finally {
                _isReprocessing.value = false
            }
        }
    }

    /**
     * Clear reprocessed text
     */
    fun clearReprocessedText() {
        _reprocessedText.value = null
        _reprocessingInfo.value = null
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
