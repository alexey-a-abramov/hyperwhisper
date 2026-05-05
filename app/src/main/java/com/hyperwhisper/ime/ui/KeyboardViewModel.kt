package com.hyperwhisper.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyperwhisper.audio.AudioRecorderManager
import com.hyperwhisper.audio.SoundManager
import com.hyperwhisper.data.*
import com.hyperwhisper.network.VoiceRepository
import com.hyperwhisper.ui.viewmodels.HistoryViewModel
import com.hyperwhisper.ui.viewmodels.RecordingViewModel
import com.hyperwhisper.ui.viewmodels.TranscriptionViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val voiceRepository: VoiceRepository,
    private val voiceCommandProcessor: VoiceCommandProcessor,
    private val settingsRepository: SettingsRepository,
    private val soundManager: SoundManager,
    private val audioRecorderManager: AudioRecorderManager,
    val modifierKeyState: com.hyperwhisper.ime.keyboard.ModifierKeyState,
    private val perAppLayoutMemory: PerAppLayoutMemory
) : ViewModel() {

    // ============================================================================
    // Per-app layout memory
    // ============================================================================

    /**
     * Package name of the editor the IME is currently attached to. The
     * service updates this on every onStartInputView before requesting a
     * layout recall, so writes from KeyboardScreen target the right app.
     * Null when the IME has no editor info (e.g. picker).
     */
    private val _currentPackage = MutableStateFlow<String?>(null)
    val currentPackage: StateFlow<String?> = _currentPackage.asStateFlow()

    /**
     * One-shot bus for "switch to this layout" commands originating outside
     * the Compose tree (i.e. the IME service's onStartInputView). replay=1
     * so a recall that completes before KeyboardScreen attaches its
     * collector still lands on the first composition. extraBufferCapacity=1
     * keeps tryEmit non-suspending if multiple recalls race.
     */
    private val _requestedLayout = MutableSharedFlow<KeyboardInputMode>(
        replay = 1,
        extraBufferCapacity = 1
    )
    val requestedLayout: SharedFlow<KeyboardInputMode> = _requestedLayout.asSharedFlow()

    init {
        // Pipe voice-command "switch to code" requests into the same channel
        // KeyboardScreen already consumes for per-app layout recalls. The IME
        // only has one apply-layout path, so consolidating sources keeps it
        // simple — voice commands and per-app memory both compete for the
        // same SharedFlow.replay slot.
        viewModelScope.launch {
            voiceCommandProcessor.keyboardModeRequest.collect { mode ->
                _requestedLayout.emit(mode)
            }
        }
    }

    /**
     * Called by VoiceInputMethodService.onStartInputView. Updates the
     * current package for subsequent record calls, and — if per-app memory
     * is enabled and we have a stored mode — emits it for KeyboardScreen
     * to apply. No emit on null/miss; the existing global lastKeyboardInputMode
     * default in KeyboardScreen handles unknown apps.
     */
    fun onEditorAttached(packageName: String?) {
        _currentPackage.value = packageName
        if (packageName.isNullOrBlank()) return
        viewModelScope.launch {
            try {
                val enabled = settingsRepository.appearanceSettings.first().perAppLayoutMemoryEnabled
                if (!enabled) return@launch
                val mode = perAppLayoutMemory.recall(packageName) ?: return@launch
                _requestedLayout.emit(mode)
            } catch (e: Exception) {
                Log.w(TAG, "Per-app layout recall failed for $packageName", e)
            }
        }
    }

    /**
     * Called by KeyboardScreen on every keyboardInputMode change so the
     * current app's per-app memory stays in sync. Gated on the master
     * toggle so disabling stops new writes too.
     */
    fun recordLayoutForCurrentApp(mode: KeyboardInputMode) {
        val pkg = _currentPackage.value ?: return
        if (pkg.isBlank()) return
        viewModelScope.launch {
            try {
                val enabled = settingsRepository.appearanceSettings.first().perAppLayoutMemoryEnabled
                if (!enabled) return@launch
                perAppLayoutMemory.remember(pkg, mode)
            } catch (e: Exception) {
                Log.w(TAG, "Per-app layout record failed for $pkg", e)
            }
        }
    }

    // Create specialized ViewModels internally
    private val recordingViewModel: RecordingViewModel = RecordingViewModel(voiceRepository)
    private val transcriptionViewModel: TranscriptionViewModel = TranscriptionViewModel(
        context,
        voiceRepository,
        voiceCommandProcessor
    )
    private val historyViewModel: HistoryViewModel = HistoryViewModel(
        voiceRepository,
        settingsRepository
    )

    companion object {
        private const val TAG = "KeyboardViewModel"
    }

    // ============================================================================
    // Recording State - Delegated to RecordingViewModel
    // ============================================================================

    val recordingState: StateFlow<RecordingState> = recordingViewModel.recordingState
    val recordingDuration: StateFlow<Long> = recordingViewModel.recordingDuration
    val walkieTalkieMode: StateFlow<Boolean> = recordingViewModel.walkieTalkieMode
    val modeChangeMessage: StateFlow<String?> = recordingViewModel.modeChangeMessage
    val needsConfirmation: StateFlow<Boolean> = recordingViewModel.needsConfirmation
    val showCancelConfirmation: StateFlow<Boolean> = recordingViewModel.showCancelConfirmation
    val finalRecordingDuration: StateFlow<Long> = recordingViewModel.finalRecordingDuration
    val recordingWasCut: StateFlow<Boolean> = recordingViewModel.recordingWasCut

    // ============================================================================
    // Transcription State - Delegated to TranscriptionViewModel
    // ============================================================================

    val transcribedText: StateFlow<String> = transcriptionViewModel.transcribedText
    val processingInfo: StateFlow<ProcessingInfo?> = transcriptionViewModel.processingInfo
    val transcriptionProgress: StateFlow<Float?> = transcriptionViewModel.transcriptionProgress
    val processingStage: StateFlow<ProcessingStage?> = transcriptionViewModel.processingStage
    val processingPhase: StateFlow<ProcessingPhase> = transcriptionViewModel.processingPhase
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

    val recentlyUsedProviderModels: StateFlow<List<ProviderModelSelection>> =
        settingsRepository.recentlyUsedProviderModels
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val configuredProviders: StateFlow<List<ApiProvider>> = apiSettings
        .map { settings ->
            ApiProvider.entries.filter { provider -> isProviderConfigured(provider, settings) }
                .ifEmpty { listOf(settings.provider) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf(ApiProvider.OPENAI))

    val usageStatistics: StateFlow<UsageStatistics> = settingsRepository.usageStatistics
        .stateIn(viewModelScope, SharingStarted.Eagerly, UsageStatistics())

    private fun isProviderConfigured(provider: ApiProvider, settings: ApiSettings): Boolean {
        val config = settings.providerConfigs[provider]
        val requiresAuth = config?.requiresAuth ?: provider.requiresAuth
        val baseUrl = config?.customBaseUrl?.ifBlank { provider.defaultEndpoint } ?: provider.defaultEndpoint
        val hasApiKey = settings.apiKeys[provider]?.isNotBlank() == true
        return baseUrl.isNotBlank() && (!requiresAuth || hasApiKey)
    }

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
        // Sync state with repository first to catch any desync issues
        syncRecordingState()

        val state = recordingState.value
        if (state == RecordingState.RECORDING || state == RecordingState.PROCESSING) {
            Log.d(TAG, "Ignoring startRecording request while state=$state")
            return
        }

        recordingViewModel.startRecording()
    }

    /**
     * Stop recording
     * If duration > 30s, will require confirmation before processing
     */
    fun stopRecording() {
        viewModelScope.launch {
            // Check if recording was cut due to timeout
            if (audioRecorderManager.wasRecordingCutDueToTimeout()) {
                Log.d(TAG, "Recording was cut due to timeout - playing sound notification")
                soundManager.playRecordingCutSound()
                recordingViewModel.setRecordingWasCut()
                audioRecorderManager.clearRecordingCutFlag()
            }

            // Stop recording
            val stopResult = recordingViewModel.stopRecording() ?: return@launch

            Log.d(TAG, "Recording stopped - processing automatically")
            processAudioFile(stopResult.audioFile)
        }
    }

    /**
     * User confirmed the recording - dismiss dialog (processing already started)
     */
    fun confirmRecording() {
        viewModelScope.launch {
            Log.d(TAG, "User confirmed - dismissing dialog (processing already in progress)")
            recordingViewModel.confirmRecording()
            recordingViewModel.clearRecordingWasCutFlag()
            // Processing already started in stopRecording(), just dismiss the dialog
        }
    }

    /**
     * User rejected the recording - cancel ongoing processing and discard
     */
    fun rejectRecording() {
        Log.d(TAG, "User rejected - canceling transcription and discarding recording")
        cancelTranscription() // Cancel any ongoing processing
        recordingViewModel.rejectRecording()
        recordingViewModel.clearRecordingWasCutFlag()
    }

    /**
     * Process audio file through transcription API
     */
    private suspend fun processAudioFile(audioFile: java.io.File) {
        Log.d(
            TAG,
            "processAudioFile: start file=${audioFile.name}, bytes=${audioFile.length()}, mode=${selectedMode.value?.id}, provider=${apiSettings.value.provider}"
        )

        // Reset transient transcription state so stale values don't affect result handling
        transcriptionViewModel.clearError()
        transcriptionViewModel.clearTranscribedText()
        transcriptionViewModel.clearProcessingInfo()

        // Set recording state to processing
        recordingViewModel.setProcessing()

        // Get current settings and mode
        val settings = apiSettings.value
        val mode = selectedMode.value
        val appearance = appearanceSettings.value
        // Always persist the source audio during processing so a failed
        // transcription / post-processing run can be reprocessed from history.
        // The original `saveOriginalAudioFiles` setting now governs whether
        // audio is *retained* after a successful run (cleanup happens below).
        val shouldSaveAudio = true
        val keepAudioAfterSuccess = appearance.saveOriginalAudioFiles
        settingsRepository.trackProviderModelUsage(settings.provider, settings.modelId)

        if (mode == null) {
            recordingViewModel.setError("No voice mode selected")
            return
        }

        // Process through transcription view model
        val savedAudioPath = transcriptionViewModel.processAudio(audioFile, mode, settings, shouldSaveAudio)
        Log.d(
            TAG,
            "processAudioFile: completed savedAudioPath=$savedAudioPath, textLength=${transcriptionViewModel.transcribedText.value.length}, error=${transcriptionViewModel.errorMessage.value}"
        )

        // Handle results deterministically so every recording has an explicit outcome
        val inWalkieTalkieMode = walkieTalkieMode.value
        val error = transcriptionViewModel.errorMessage.value
        val text = transcriptionViewModel.transcribedText.value
        val hasPendingCommand = transcriptionViewModel.pendingCommandResult.value != null

        when (determineProcessingOutcome(error, text, hasPendingCommand)) {
            ProcessingOutcome.ERROR -> {
                soundManager.playErrorSound()
                recordingViewModel.setError(error ?: "Unknown transcription error")
                if (!inWalkieTalkieMode) {
                    historyViewModel.addToHistory("", savedAudioPath)
                }
                Log.d(TAG, "processAudioFile: finished with error")
            }
            ProcessingOutcome.PENDING_COMMAND -> {
                // Configuration mode may intentionally not emit transcribed text
                soundManager.playSuccessSound()
                recordingViewModel.setIdle()
                if (!inWalkieTalkieMode) {
                    historyViewModel.addToHistory("", savedAudioPath)
                }
                Log.d(TAG, "processAudioFile: finished with pending configuration command")
            }
            ProcessingOutcome.SUCCESS -> {
                soundManager.playSuccessSound()
                recordingViewModel.setIdle()
                if (!inWalkieTalkieMode) {
                    // Honour the user's "save original audio files" setting on
                    // success: if they opted out, drop the audio path before
                    // recording history (the file gets cleaned up by the temp
                    // delete below).
                    val historyAudioPath = if (keepAudioAfterSuccess) savedAudioPath else null
                    if (!keepAudioAfterSuccess) savedAudioPath?.let { runCatching { java.io.File(it).delete() } }
                    historyViewModel.addToHistory(text, historyAudioPath)
                }
                Log.d(TAG, "processAudioFile: finished successfully with text")
            }
            ProcessingOutcome.EMPTY_TRANSCRIPTION -> {
                val noResultMessage = "Recording processed, but no speech was detected. Try speaking louder or recording a bit longer."
                soundManager.playErrorSound()
                recordingViewModel.setError(noResultMessage)
                if (!inWalkieTalkieMode) {
                    historyViewModel.addToHistory("", savedAudioPath)
                }
                Log.d(TAG, "processAudioFile: finished with empty transcription")
            }
        }

        // Cleanup audio file
        audioFile.delete()
        recordingViewModel.clearRecordedAudioFile()
    }

    /**
     * Cancel recording
     */
    fun cancelRecording() {
        recordingViewModel.cancelRecording()
    }

    /**
     * User confirmed they want to cancel the recording
     */
    fun confirmCancelRecording() {
        recordingViewModel.confirmCancelRecording()
    }

    /**
     * User dismissed the cancel confirmation dialog
     */
    fun dismissCancelConfirmation() {
        recordingViewModel.dismissCancelConfirmation()
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

    /**
     * Save keyboard input mode preference
     */
    fun saveKeyboardInputMode(settings: AppearanceSettings) {
        viewModelScope.launch {
            settingsRepository.saveAppearanceSettings(settings)
            Log.d(TAG, "Keyboard input mode saved: ${settings.lastKeyboardInputMode}")
        }
    }

    /**
     * Update keyboard layout.
     */
    fun updateKeyboardLayout(settings: AppearanceSettings) {
        viewModelScope.launch {
            settingsRepository.saveAppearanceSettings(settings)
            Log.d(TAG, "Keyboard layout updated: ${settings.currentKeyboardLayout}")
        }
    }

    /**
     * Update recent emojis.
     */
    fun updateRecentEmojis(settings: AppearanceSettings) {
        viewModelScope.launch {
            settingsRepository.saveAppearanceSettings(settings)
            Log.d(TAG, "Recent emojis updated")
        }
    }

    /**
     * Bind a new mode to the configurable third slot of the top strip.
     */
    fun setPresetKeyboardMode(mode: com.hyperwhisper.data.KeyboardInputMode) {
        viewModelScope.launch {
            val current = appearanceSettings.value
            settingsRepository.saveAppearanceSettings(
                current.copy(presetKeyboardMode = mode)
            )
            Log.d(TAG, "Preset keyboard mode set to $mode")
        }
    }

    /**
     * Update the LLM post-processing provider + model from the keyboard's
     * inline picker. API key and custom base URL persist — the user manages
     * those in Settings, since the data model holds a single LlmConfig
     * (no per-provider key map yet).
     */
    fun setLlmProviderAndModel(provider: com.hyperwhisper.data.LlmProvider, modelId: String) {
        viewModelScope.launch {
            val current = apiSettings.value
            val updated = current.copy(
                llmConfig = current.llmConfig.copy(
                    provider = provider,
                    modelId = modelId
                )
            )
            settingsRepository.saveApiSettings(updated)
            Log.d(TAG, "LLM config changed to: ${provider.name} / $modelId")
        }
    }

    /**
     * Set provider and model from quick picker.
     *
     * Also toggles `localModelSettings.useLocalWhisper` to match the chosen
     * provider — picking [ApiProvider.LOCAL_WHISPER] enables on-device mode,
     * picking anything else falls back to cloud routing. Without this the
     * router gets a mismatched (provider, useLocalWhisper) pair and either
     * asks for an API key when the user wanted local, or tries to load a
     * local model file when they wanted cloud.
     */
    fun setProviderAndModel(provider: ApiProvider, modelId: String) {
        viewModelScope.launch {
            val currentSettings = apiSettings.value
            val providerBaseUrl = currentSettings.providerConfigs[provider]
                ?.customBaseUrl
                ?.ifEmpty { provider.defaultEndpoint }
                ?: provider.defaultEndpoint
            val pickingLocal = provider == ApiProvider.LOCAL_WHISPER
            val updatedLocalSettings = currentSettings.localModelSettings.copy(
                useLocalWhisper = pickingLocal
            )
            val updatedSettings = currentSettings.copy(
                provider = provider,
                baseUrl = providerBaseUrl,
                modelId = modelId,
                localModelSettings = updatedLocalSettings
            )
            settingsRepository.saveApiSettings(updatedSettings)
            settingsRepository.trackProviderModelUsage(provider, modelId)
            Log.d(TAG, "Provider/model changed to: ${provider.displayName} / $modelId (local=$pickingLocal)")
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
     * Enable walkie-talkie mode
     */
    fun enableWalkieTalkieMode() {
        viewModelScope.launch {
            // Use localized string - need to get from string resources
            recordingViewModel.enableWalkieTalkieMode(
                "Walkie-Talkie mode enabled. To exit, double-tap the button."
            )
        }
    }

    /**
     * Disable walkie-talkie mode
     */
    fun disableWalkieTalkieMode() {
        viewModelScope.launch {
            recordingViewModel.disableWalkieTalkieMode(
                "Normal mode enabled. Long press to activate Walkie-Talkie mode."
            )
        }
    }

    /**
     * Clear mode change message
     */
    fun clearModeChangeMessage() {
        recordingViewModel.clearModeChangeMessage()
    }

    /**
     * Sync recording state from repository when IME view starts again.
     */
    fun syncRecordingState() {
        recordingViewModel.syncRecordingState()
    }

    /**
     * Reset state
     */
    fun reset() {
        recordingViewModel.clearError()
        transcriptionViewModel.clearTranscribedText()
        transcriptionViewModel.clearError()
    }

    init {
        // Set up callback for when max recording duration is reached
        audioRecorderManager.onMaxDurationReached = {
            Log.d(TAG, "Max recording duration reached - auto-stopping recording")
            viewModelScope.launch {
                stopRecording()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundManager.release()
    }
}
