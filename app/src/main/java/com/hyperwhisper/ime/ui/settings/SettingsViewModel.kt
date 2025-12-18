package com.hyperwhisper.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.SettingsRepository
import com.hyperwhisper.data.VoiceMode
import com.hyperwhisper.network.ChatCompletionApiService
import com.hyperwhisper.network.TranscriptionApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
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

    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    fun saveApiSettings(
        provider: ApiProvider,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        inputLanguage: String = "",
        outputLanguage: String = ""
    ) {
        viewModelScope.launch {
            try {
                // Get current settings to preserve other provider API keys
                val currentSettings = apiSettings.value
                val updatedApiKeys = currentSettings.apiKeys.toMutableMap()
                updatedApiKeys[provider] = apiKey.trim()

                val settings = ApiSettings(
                    provider = provider,
                    baseUrl = baseUrl.trim(),
                    apiKeys = updatedApiKeys,
                    modelId = modelId.trim(),
                    inputLanguage = inputLanguage.trim(),
                    outputLanguage = outputLanguage.trim()
                )
                settingsRepository.saveApiSettings(settings)
                Log.d(TAG, "API settings saved: $provider, $baseUrl, model: $modelId")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving API settings", e)
            }
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

    fun testConnection(baseUrl: String, apiKey: String, modelId: String) {
        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState.Testing
            Log.d(TAG, "Testing connection to: $baseUrl")

            try {
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
}
