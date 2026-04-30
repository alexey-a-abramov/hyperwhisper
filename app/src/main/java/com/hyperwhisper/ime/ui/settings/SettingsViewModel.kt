package com.hyperwhisper.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.hyperwhisper.data.ApiCallLog
import com.hyperwhisper.data.ApiCallStatistics
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.AppearanceSettings
import com.hyperwhisper.data.SettingsRepository
import com.hyperwhisper.data.VoiceMode
import com.hyperwhisper.network.ChatCompletionApiService
import com.hyperwhisper.network.TranscriptionApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.*
import javax.inject.Inject

sealed class ConnectionTestState {
    object Idle : ConnectionTestState()
    object Testing : ConnectionTestState()
    data class Success(val message: String) : ConnectionTestState()
    data class Error(val message: String) : ConnectionTestState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val transcriptionApiService: TranscriptionApiService,
    private val chatCompletionApiService: ChatCompletionApiService
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

    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    private val _discoveredModels = MutableStateFlow<List<com.hyperwhisper.data.LocalModelInfo>>(emptyList())
    val discoveredModels: StateFlow<List<com.hyperwhisper.data.LocalModelInfo>> = _discoveredModels.asStateFlow()

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

    fun testConnection(provider: ApiProvider, baseUrl: String, apiKey: String, modelId: String) {
        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState.Testing
            Log.d(TAG, "Testing connection to: $baseUrl")

            try {
                if (provider == ApiProvider.SELFHOSTED_WHISPER) {
                    val request = Request.Builder()
                        .url(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                        .get()
                        .build()
                    OkHttpClient.Builder().build().newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            _connectionTestState.value = ConnectionTestState.Success(
                                "Connection successful! Local whisper.cpp server is responding."
                            )
                            Log.d(TAG, "Local whisper.cpp connection test successful")
                            return@launch
                        }
                        throw IllegalStateException("HTTP ${response.code}")
                    }
                }

                // Create a minimal test request - empty audio file
                val emptyAudio = ByteArray(44) // Minimal WAV header
                val audioPart = MultipartBody.Part.createFormData(
                    "file",
                    "test.wav",
                    emptyAudio.toRequestBody("audio/wav".toMediaTypeOrNull())
                )
                val modelPart = modelId.toRequestBody("text/plain".toMediaTypeOrNull())

                // Attempt transcription call (most common endpoint)
                val response = transcriptionApiService.transcribe(audioPart, modelPart)

                _connectionTestState.value = ConnectionTestState.Success(
                    "Connection successful! API is responding."
                )
                Log.d(TAG, "Connection test successful")
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("401") == true -> "Authentication failed. Check your API key."
                    e.message?.contains("404") == true -> "Endpoint not found. Check base URL and model ID."
                    e.message?.contains("timeout") == true -> "Connection timeout. Check your internet connection."
                    e.message?.contains("SSL") == true -> "SSL/TLS error. Check endpoint URL (https)."
                    else -> "Connection failed: ${e.message ?: "Unknown error"}"
                }

                _connectionTestState.value = ConnectionTestState.Error(errorMessage)
                Log.e(TAG, "Connection test failed", e)
            }
        }
    }

    fun resetConnectionTestState() {
        _connectionTestState.value = ConnectionTestState.Idle
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
