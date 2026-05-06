package com.hyperwhisper.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.hyperwhisper.data.ApiCallLog
import com.hyperwhisper.data.ApiCallStatistics
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.AppearanceSettings
import com.hyperwhisper.data.SettingsRepository
import com.hyperwhisper.data.VoiceMode
import com.hyperwhisper.data.WhisperDownloadState
import com.hyperwhisper.data.WhisperModelCatalog
import com.hyperwhisper.data.WhisperModelDownloader
import com.hyperwhisper.network.ConnectionTester
import com.hyperwhisper.network.OpenRouterDiscoveryService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

sealed class ConnectionTestState {
    object Idle : ConnectionTestState()
    object Testing : ConnectionTestState()
    data class Success(val message: String) : ConnectionTestState()
    data class Error(val message: String) : ConnectionTestState()
}

enum class TestLogLevel { INFO, RUNNING, OK, FAIL }

data class TestLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: TestLogLevel,
    val message: String,
    val detail: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val gson: Gson,
    private val whisperDownloader: WhisperModelDownloader,
    private val gemmaDownloader: com.hyperwhisper.data.GemmaModelDownloader,
    private val connectionTester: ConnectionTester,
    private val openRouterDiscoveryService: OpenRouterDiscoveryService
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    val apiSettings: StateFlow<ApiSettings> = settingsRepository.apiSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ApiSettings())

    val voiceModes: StateFlow<List<VoiceMode>> = settingsRepository.voiceModes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val appearanceSettings: StateFlow<AppearanceSettings> = settingsRepository.appearanceSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceSettings())

    val apiCallLogs: StateFlow<List<ApiCallLog>> = settingsRepository.apiCallLogs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _apiCallStatistics = MutableStateFlow(ApiCallStatistics(0, 0, 0, emptyMap(), 0))
    val apiCallStatistics: StateFlow<ApiCallStatistics> = _apiCallStatistics.asStateFlow()

    // Test state is owned by ConnectionTester; we re-expose its StateFlows
    // unchanged so existing UI references (viewModel.connectionTestState etc.)
    // continue to compile against the same types.
    val connectionTestState: StateFlow<ConnectionTestState> = connectionTester.connectionTestState
    val postProcessingTestState: StateFlow<ConnectionTestState> = connectionTester.postProcessingTestState
    val transcriptionTestLog: StateFlow<List<TestLogEntry>> = connectionTester.transcriptionTestLog
    val postProcessingTestLog: StateFlow<List<TestLogEntry>> = connectionTester.postProcessingTestLog

    val retestProgress: StateFlow<Map<String, ConnectionTester.RetestRowState>> =
        connectionTester.retestProgress
    val retestRunning: StateFlow<Boolean> = connectionTester.retestRunning

    /** Re-run every configured ASR + LLM provider's connection test, refreshing
     *  the per-provider lastTestedAt timestamps that the picker badges read. */
    fun retestAllProviders() {
        viewModelScope.launch {
            try {
                connectionTester.retestAll()
            } catch (t: Throwable) {
                Log.w(TAG, "Retest-all failed", t)
            }
        }
    }

    fun resetRetestProgress() {
        connectionTester.resetRetestProgress()
    }

    private val _discoveredModels = MutableStateFlow<List<com.hyperwhisper.data.LocalModelInfo>>(emptyList())
    val discoveredModels: StateFlow<List<com.hyperwhisper.data.LocalModelInfo>> = _discoveredModels.asStateFlow()

    val whisperDownloadStates: StateFlow<Map<String, WhisperDownloadState>> = whisperDownloader.states

    private val _integrationResults =
        MutableStateFlow<List<com.hyperwhisper.ui.about.ProviderIntegrationResult>>(emptyList())
    val integrationResults: StateFlow<List<com.hyperwhisper.ui.about.ProviderIntegrationResult>> =
        _integrationResults.asStateFlow()

    private val _integrationRunning = MutableStateFlow(false)
    val integrationRunning: StateFlow<Boolean> = _integrationRunning.asStateFlow()

    /** Run the integration probe across every [ApiProvider]. Hits configured
     *  providers with a real HTTP probe; skips ones with no key set. */
    fun runIntegrationTests() {
        viewModelScope.launch {
            _integrationRunning.value = true
            try {
                val runner = com.hyperwhisper.ui.about.ProviderIntegrationTestRunner(gson)
                _integrationResults.value = runner.runAll(apiSettings.value)
            } catch (t: Throwable) {
                Log.w(TAG, "Integration tests failed", t)
            } finally {
                _integrationRunning.value = false
            }
        }
    }
    val gemmaDownloadStates: StateFlow<Map<String, com.hyperwhisper.data.GemmaDownloadState>> =
        gemmaDownloader.states

    fun startGemmaDownload(modelId: String) {
        val entry = com.hyperwhisper.data.GemmaModelCatalog.byId(modelId) ?: return
        gemmaDownloader.start(entry)
    }
    fun cancelGemmaDownload(modelId: String) = gemmaDownloader.cancel(modelId)
    fun deleteDownloadedGemmaModel(modelId: String) {
        val entry = com.hyperwhisper.data.GemmaModelCatalog.byId(modelId) ?: return
        gemmaDownloader.delete(entry)
    }
    fun setActiveGemmaModel(path: String) {
        viewModelScope.launch {
            val s = apiSettings.value.localModelSettings
            settingsRepository.updateLocalModelSettings(
                s.copy(gemmaModelPath = path, useLocalGemma = true)
            )
        }
    }

    /**
     * Delete an on-disk model file that the user no longer wants. Used by the
     * "Detected on disk" section to clean up incompatible (e.g. .gguf) files.
     * If the deleted file was the active Gemma path, clear that too so the
     * settings don't keep pointing at a missing file.
     */
    fun deleteOnDiskFile(path: String) {
        viewModelScope.launch {
            try {
                java.io.File(path).takeIf { it.exists() }?.delete()
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to delete on-disk file $path", t)
            }
            val s = apiSettings.value.localModelSettings
            if (s.gemmaModelPath == path) {
                settingsRepository.updateLocalModelSettings(
                    s.copy(gemmaModelPath = "", useLocalGemma = false)
                )
            }
            discoverModels()
        }
    }

    // OpenRouter discovery is owned by OpenRouterDiscoveryService; same
    // re-expose pattern as the test state above.
    val openRouterModels: StateFlow<List<com.hyperwhisper.network.OpenRouterModelInfo>> =
        openRouterDiscoveryService.openRouterModels
    val openRouterRefreshing: StateFlow<Boolean> = openRouterDiscoveryService.openRouterRefreshing
    val openRouterError: StateFlow<String?> = openRouterDiscoveryService.openRouterError

    init {
        // Update statistics whenever logs change
        viewModelScope.launch {
            apiCallLogs.collect {
                _apiCallStatistics.value = settingsRepository.getApiCallStatistics()
            }
        }

        // Initial model discovery
        viewModelScope.launch {
            apiSettings.collect { settings ->
                if (settings.localModelSettings.autoDiscover && _discoveredModels.value.isEmpty()) {
                    discoverModels()
                }
            }
        }
    }

    fun discoverModels() {
        viewModelScope.launch {
            try {
                _discoveredModels.value = settingsRepository.localModelRepository.discoverModels()
                Log.d(TAG, "Discovered ${_discoveredModels.value.size} local models")
            } catch (e: Exception) {
                Log.e(TAG, "Error discovering models", e)
            }
        }
    }

    fun verifyModelIntegrity(path: String) {
        viewModelScope.launch {
            try {
                val hash = settingsRepository.localModelRepository.verifyIntegrity(path)
                // Update the model info in our list with the hash
                _discoveredModels.value = _discoveredModels.value.map {
                    if (it.path == path) it.copy(hash = hash, isVerified = hash.isNotEmpty()) else it
                }
                Log.d(TAG, "Verified model integrity for: $path, hash: $hash")
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying model integrity", e)
            }
        }
    }

    fun updateLocalModelSettings(settings: com.hyperwhisper.data.LocalModelSettings) {
        viewModelScope.launch {
            try {
                settingsRepository.updateLocalModelSettings(settings)
                Log.d(TAG, "Local model settings updated: $settings")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating local model settings", e)
            }
        }
    }

    fun saveApiSettings(
        provider: ApiProvider,
        baseUrl: String,
        apiKey: String,
        requiresAuth: Boolean,
        modelId: String,
        inputLanguage: String = "",
        outputLanguage: String = ""
    ) {
        viewModelScope.launch {
            saveApiSettingsInternal(provider, baseUrl, apiKey, requiresAuth, modelId, inputLanguage, outputLanguage)
        }
    }

    /**
     * Suspend version that waits for the save to complete before returning.
     * Use this when you need to ensure settings are saved before proceeding (e.g., before closing activity).
     */
    suspend fun saveApiSettingsAndWait(
        provider: ApiProvider,
        baseUrl: String,
        apiKey: String,
        requiresAuth: Boolean,
        modelId: String,
        inputLanguage: String = "",
        outputLanguage: String = ""
    ) {
        saveApiSettingsInternal(provider, baseUrl, apiKey, requiresAuth, modelId, inputLanguage, outputLanguage)
    }

    private suspend fun saveApiSettingsInternal(
        provider: ApiProvider,
        baseUrl: String,
        apiKey: String,
        requiresAuth: Boolean,
        modelId: String,
        inputLanguage: String,
        outputLanguage: String
    ) {
        try {
            // Get current settings to preserve other provider data and LLM config
            val currentSettings = apiSettings.value
            val updatedApiKeys = currentSettings.apiKeys.toMutableMap()
            updatedApiKeys[provider] = apiKey.trim()

            val updatedConfigs = currentSettings.providerConfigs.toMutableMap()
            updatedConfigs[provider] = com.hyperwhisper.data.ProviderConfig(
                customBaseUrl = baseUrl.trim(),
                requiresAuth = requiresAuth
            )

            val settings = ApiSettings(
                provider = provider,
                baseUrl = baseUrl.trim(),
                apiKeys = updatedApiKeys,
                providerConfigs = updatedConfigs,
                modelId = modelId.trim(),
                inputLanguage = inputLanguage.trim(),
                outputLanguage = outputLanguage.trim(),
                llmConfig = currentSettings.llmConfig // Preserve LLM config
            )
            settingsRepository.saveApiSettings(settings)
            Log.d(TAG, "API settings saved: $provider, $baseUrl, requiresAuth: $requiresAuth, model: $modelId")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving API settings", e)
        }
    }

    fun updateProviderApiKey(provider: ApiProvider, apiKey: String) {
        viewModelScope.launch {
            try {
                settingsRepository.updateProviderApiKey(provider, apiKey.trim())
                Log.d(TAG, "API key updated for provider: ${provider.displayName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating provider API key", e)
            }
        }
    }

    fun updateProviderConfig(provider: ApiProvider, customBaseUrl: String, requiresAuth: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.updateProviderConfig(provider, customBaseUrl.trim(), requiresAuth)
                Log.d(TAG, "Provider config updated for: ${provider.displayName}, requiresAuth: $requiresAuth")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating provider config", e)
            }
        }
    }

    fun updateLlmConfig(llmConfig: com.hyperwhisper.data.LlmConfig) {
        viewModelScope.launch {
            try {
                settingsRepository.updateLlmConfig(llmConfig)
                Log.d(TAG, "LLM config updated: ${llmConfig.provider.displayName}, model: ${llmConfig.modelId}")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating LLM config", e)
            }
        }
    }

    fun resetToDefaults(provider: ApiProvider) {
        viewModelScope.launch {
            try {
                settingsRepository.resetApiSettingsToDefaults(provider)
                Log.d(TAG, "Reset settings to defaults for provider: ${provider.displayName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting to defaults", e)
            }
        }
    }

    fun saveAppearanceSettings(settings: AppearanceSettings) {
        viewModelScope.launch {
            try {
                // Check if history size is being reduced
                val currentSettings = appearanceSettings.value
                val needsTrimming = !settings.unlimitedHistory &&
                    (settings.maxHistoryItems < currentSettings.maxHistoryItems ||
                     (currentSettings.unlimitedHistory && !settings.unlimitedHistory))

                // Save settings first
                settingsRepository.saveAppearanceSettings(settings)
                Log.d(TAG, "Appearance settings saved: $settings")

                // Trim history if needed
                if (needsTrimming) {
                    settingsRepository.trimHistoryToSize(settings.maxHistoryItems)
                    Log.d(TAG, "History trimmed to ${settings.maxHistoryItems} items")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving appearance settings", e)
            }
        }
    }

    fun addVoiceMode(name: String, systemPrompt: String) {
        viewModelScope.launch {
            try {
                val mode = VoiceMode(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    systemPrompt = systemPrompt.trim(),
                    isBuiltIn = false
                )
                settingsRepository.addVoiceMode(mode)
                Log.d(TAG, "Voice mode added: ${mode.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding voice mode", e)
            }
        }
    }

    fun deleteVoiceMode(modeId: String) {
        viewModelScope.launch {
            try {
                settingsRepository.deleteVoiceMode(modeId)
                Log.d(TAG, "Voice mode deleted: $modeId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting voice mode", e)
            }
        }
    }

    fun updateVoiceMode(mode: VoiceMode) {
        viewModelScope.launch {
            try {
                settingsRepository.updateVoiceMode(mode)
                Log.d(TAG, "Voice mode updated: ${mode.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating voice mode", e)
            }
        }
    }

    /** Forwards to [ConnectionTester.testConnection]. The tester owns
     *  [connectionTestState] and [transcriptionTestLog]. */
    fun testConnection(provider: ApiProvider, baseUrl: String, apiKey: String, modelId: String) {
        viewModelScope.launch {
            connectionTester.testConnection(provider, baseUrl, apiKey, modelId)
        }
    }

    fun resetConnectionTestState() {
        connectionTester.resetConnectionTestState()
    }

    /** Forwards to [ConnectionTester.testPostProcessing]. The tester owns
     *  [postProcessingTestState] and [postProcessingTestLog]. */
    fun testPostProcessing(voiceMode: VoiceMode) {
        viewModelScope.launch {
            connectionTester.testPostProcessing(voiceMode)
        }
    }

    fun resetPostProcessingTestState() {
        connectionTester.resetPostProcessingTestState()
    }

    /**
     * Export all provider secrets/config as pretty JSON for external integration tests.
     */
    fun buildSecretsExportJson(): String {
        val settings = apiSettings.value
        val providers = ApiProvider.entries.associate { provider ->
            val providerConfig = settings.providerConfigs[provider]
            provider.name to mapOf(
                "displayName" to provider.displayName,
                "baseUrl" to (providerConfig?.customBaseUrl?.ifBlank { provider.defaultEndpoint } ?: provider.defaultEndpoint),
                "requiresAuth" to (providerConfig?.requiresAuth ?: provider.requiresAuth),
                "apiKey" to (settings.apiKeys[provider] ?: ""),
                "modelId" to if (settings.provider == provider) settings.modelId else (provider.defaultModels.firstOrNull() ?: "")
            )
        }

        val payload = mapOf(
            "format" to "hyperwhisper-secrets-v1",
            "currentProvider" to settings.provider.name,
            "providers" to providers
        )
        return GsonBuilder().setPrettyPrinting().create().toJson(payload)
    }

    /** Forwards to [OpenRouterDiscoveryService.refreshOpenRouterModels]. */
    fun refreshOpenRouterModels() {
        viewModelScope.launch {
            openRouterDiscoveryService.refreshOpenRouterModels()
        }
    }

    fun startWhisperDownload(modelId: String) {
        val entry = WhisperModelCatalog.byId(modelId) ?: return
        whisperDownloader.start(entry)
    }

    fun cancelWhisperDownload(modelId: String) {
        whisperDownloader.cancel(modelId)
    }

    fun deleteDownloadedWhisperModel(modelId: String) {
        val entry = WhisperModelCatalog.byId(modelId) ?: return
        val activePath = apiSettings.value.localModelSettings.whisperModelPath
        val targetPath = whisperDownloader.targetFile(entry).absolutePath
        whisperDownloader.delete(entry)
        viewModelScope.launch {
            if (activePath == targetPath) {
                val cleared = apiSettings.value.localModelSettings.copy(
                    whisperModelPath = "",
                    whisperModelHash = "",
                    useLocalWhisper = false
                )
                settingsRepository.updateLocalModelSettings(cleared)
            }
            discoverModels()
        }
    }

    /** Mark the cloud configuration (current `apiSettings.provider`) as the active source. */
    fun setActiveCloudProvider() {
        viewModelScope.launch {
            val s = apiSettings.value.localModelSettings
            if (s.useLocalWhisper) {
                settingsRepository.updateLocalModelSettings(s.copy(useLocalWhisper = false))
            }
        }
    }

    /** Mark a specific local Whisper model as active (and switch to local mode). */
    fun setActiveLocalWhisperModel(path: String) {
        viewModelScope.launch {
            val s = apiSettings.value.localModelSettings
            settingsRepository.updateLocalModelSettings(
                s.copy(whisperModelPath = path, useLocalWhisper = true)
            )
        }
    }

    /**
     * Clear all API call logs
     */
    fun clearApiCallLogs() {
        viewModelScope.launch {
            settingsRepository.clearApiCallLogs()
            Log.d(TAG, "Cleared all API call logs")
        }
    }
}
