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
     * Reconcile saved audio files against history — surfaces orphaned audio
     * (saved but never linked) and normalises legacy file names. Runs off the
     * main thread; the resulting rows flow back through [transcriptionHistory].
     */
    fun reconcileAudioHistory() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { settingsRepository.reconcileAudioFiles() }
                .onFailure { Log.e(TAG, "Audio reconcile failed", it) }
        }
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
     * Reprocess audio from history with current settings. Suspends until the
     * run resolves so the caller can drive progress UI around it and react to
     * success/failure deterministically (on error nothing is emitted to
     * [reprocessedText] — check [errorMessage]).
     */
    suspend fun reprocessWithCurrentSettings(
        item: TranscriptionHistoryItem,
        currentSettings: ApiSettings,
        currentMode: VoiceMode?
    ) {
        try {
            val audioFilePath = item.audioFilePath
            if (audioFilePath == null) {
                _errorMessage.value = "No audio file available for reprocessing"
                return
            }

            val audioFile = File(audioFilePath)
            if (!audioFile.exists()) {
                _errorMessage.value = "Audio file no longer exists"
                return
            }

            // Skip the cloud-key gate when on-device Whisper is active —
            // local processing doesn't need a cloud key.
            if (!currentSettings.localModelSettings.useLocalWhisper &&
                currentSettings.getCurrentApiKey().isBlank()) {
                _errorMessage.value = "Please configure API key for ${currentSettings.provider.displayName} in settings"
                return
            }

            if (currentMode == null) {
                _errorMessage.value = "No voice mode selected"
                return
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
