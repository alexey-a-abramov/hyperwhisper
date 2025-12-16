package com.hyperwhisper.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.SettingsRepository
import com.hyperwhisper.data.VoiceMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    val apiSettings: StateFlow<ApiSettings> = settingsRepository.apiSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ApiSettings())

    val voiceModes: StateFlow<List<VoiceMode>> = settingsRepository.voiceModes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveApiSettings(
        provider: ApiProvider,
        baseUrl: String,
        apiKey: String,
        modelId: String
    ) {
        viewModelScope.launch {
            try {
                val settings = ApiSettings(
                    provider = provider,
                    baseUrl = baseUrl.trim(),
                    apiKey = apiKey.trim(),
                    modelId = modelId.trim()
                )
                settingsRepository.saveApiSettings(settings)
                Log.d(TAG, "API settings saved: $provider, $baseUrl")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving API settings", e)
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
}
