package com.hyperwhisper.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.AppearanceSettings
import com.hyperwhisper.data.LocalModelValidator
import com.hyperwhisper.data.LocalSettings
import com.hyperwhisper.data.ModelDownloadState
import com.hyperwhisper.data.ModelRepository
import com.hyperwhisper.data.SettingsRepository
import com.hyperwhisper.data.VoiceMode
import com.hyperwhisper.data.WhisperModel
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
    private val chatCompletionApiService: ChatCompletionApiService,
    private val modelRepository: ModelRepository,
    private val localModelValidator: LocalModelValidator
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

    val modelStates: StateFlow<Map<WhisperModel, ModelDownloadState>> = modelRepository.modelStates
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    fun saveApiSettings(
        provider: ApiProvider,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        inputLanguage: String = "",
        outputLanguage: String = "",
        localSettings: LocalSettings = LocalSettings()
    ) {
        viewModelScope.launch {
            try {
                // Log warning if LOCAL provider is selected but model is not downloaded
                // Don't block saving - user should be able to save their preference
                if (provider == ApiProvider.LOCAL) {
                    val validationResult = localModelValidator.validateModel(localSettings.selectedModel)
                    if (validationResult.isFailure) {
                        Log.w(TAG, "LOCAL provider selected but model not ready: ${validationResult.exceptionOrNull()?.message}")
                        Log.w(TAG, "Settings will be saved but model must be downloaded before use")
                    }
                }

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
                    outputLanguage = outputLanguage.trim(),
                    localSettings = localSettings
                )
                settingsRepository.saveApiSettings(settings)
                Log.d(TAG, "API settings saved: $provider, $baseUrl, model: $modelId, local: ${localSettings.selectedModel.displayName}")
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

    fun saveAppearanceSettings(settings: AppearanceSettings) {
        viewModelScope.launch {
            try {
                settingsRepository.saveAppearanceSettings(settings)
                Log.d(TAG, "Appearance settings saved: $settings")
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

    /**
     * Download a whisper.cpp model
     */
    fun downloadModel(model: WhisperModel) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting download for model: ${model.displayName}")
                val result = modelRepository.downloadModel(model)

                if (result.isSuccess) {
                    Log.d(TAG, "Model downloaded successfully: ${model.displayName}")
                } else {
                    Log.e(TAG, "Model download failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading model: ${model.displayName}", e)
            }
        }
    }

    /**
     * Validate a local whisper model
     */
    suspend fun validateLocalModel(model: WhisperModel): Result<Boolean> {
        return try {
            localModelValidator.validateModel(model)
        } catch (e: Exception) {
            Log.e(TAG, "Error validating model: ${model.displayName}", e)
            Result.failure(e)
        }
    }
}
