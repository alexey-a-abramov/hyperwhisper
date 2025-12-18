package com.hyperwhisper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hyperwhisper_settings")

@Singleton
class SettingsRepository @Inject constructor(
    private val context: Context,
    private val gson: Gson
) {
    private val dataStore = context.dataStore

    companion object {
        private val API_PROVIDER_KEY = stringPreferencesKey("api_provider")
        private val BASE_URL_KEY = stringPreferencesKey("base_url")
        private val API_KEYS_MAP_KEY = stringPreferencesKey("api_keys_map") // Per-provider API keys as JSON
        private val MODEL_ID_KEY = stringPreferencesKey("model_id")
        private val INPUT_LANGUAGE_KEY = stringPreferencesKey("input_language")
        private val OUTPUT_LANGUAGE_KEY = stringPreferencesKey("output_language")
        private val VOICE_MODES_KEY = stringPreferencesKey("voice_modes")
        private val SELECTED_MODE_KEY = stringPreferencesKey("selected_mode")

        // Legacy key for migration
        private val API_KEY_KEY = stringPreferencesKey("api_key")
    }

    /**
     * API Settings Flow
     */
    val apiSettings: Flow<ApiSettings> = dataStore.data.map { preferences ->
        val provider = preferences[API_PROVIDER_KEY]?.let { ApiProvider.valueOf(it) } ?: ApiProvider.OPENAI

        // Parse API keys map or migrate from legacy single key
        val apiKeysMap = try {
            val json = preferences[API_KEYS_MAP_KEY]
            if (json.isNullOrEmpty()) {
                // Migration: check for legacy single API key
                val legacyKey = preferences[API_KEY_KEY]
                if (!legacyKey.isNullOrEmpty()) {
                    mapOf(provider to legacyKey)
                } else {
                    emptyMap()
                }
            } else {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val stringMap: Map<String, String> = gson.fromJson(json, type)
                // Convert String keys to ApiProvider enum
                stringMap.mapKeys { ApiProvider.valueOf(it.key) }
            }
        } catch (e: Exception) {
            emptyMap()
        }

        ApiSettings(
            provider = provider,
            baseUrl = preferences[BASE_URL_KEY] ?: provider.defaultEndpoint,
            apiKeys = apiKeysMap,
            modelId = preferences[MODEL_ID_KEY] ?: provider.defaultModels.firstOrNull() ?: "whisper-1",
            inputLanguage = preferences[INPUT_LANGUAGE_KEY] ?: "",
            outputLanguage = preferences[OUTPUT_LANGUAGE_KEY] ?: ""
        )
    }

    suspend fun saveApiSettings(settings: ApiSettings) {
        dataStore.edit { preferences ->
            preferences[API_PROVIDER_KEY] = settings.provider.name
            // Ensure base URL ends with /
            val normalizedUrl = if (settings.baseUrl.isNotEmpty() && !settings.baseUrl.endsWith("/")) {
                settings.baseUrl + "/"
            } else {
                settings.baseUrl
            }
            preferences[BASE_URL_KEY] = normalizedUrl

            // Save API keys map
            val stringMap = settings.apiKeys.mapKeys { it.key.name }
            preferences[API_KEYS_MAP_KEY] = gson.toJson(stringMap)

            preferences[MODEL_ID_KEY] = settings.modelId
            preferences[INPUT_LANGUAGE_KEY] = settings.inputLanguage
            preferences[OUTPUT_LANGUAGE_KEY] = settings.outputLanguage
        }
    }

    /**
     * Update API key for a specific provider
     */
    suspend fun updateProviderApiKey(provider: ApiProvider, apiKey: String) {
        dataStore.edit { preferences ->
            val currentSettings = apiSettings.first()
            val updatedKeys = currentSettings.apiKeys.toMutableMap()
            updatedKeys[provider] = apiKey

            val stringMap = updatedKeys.mapKeys { it.key.name }
            preferences[API_KEYS_MAP_KEY] = gson.toJson(stringMap)
        }
    }

    /**
     * Reset API settings to provider defaults
     */
    suspend fun resetApiSettingsToDefaults(provider: ApiProvider) {
        dataStore.edit { preferences ->
            preferences[BASE_URL_KEY] = provider.defaultEndpoint
            preferences[MODEL_ID_KEY] = provider.defaultModels.firstOrNull() ?: "whisper-1"
            // Keep API key and language as-is
        }
    }

    /**
     * Voice Modes Management
     */
    val voiceModes: Flow<List<VoiceMode>> = dataStore.data.map { preferences ->
        val modesJson = preferences[VOICE_MODES_KEY]
        if (modesJson.isNullOrEmpty()) {
            getDefaultModes()
        } else {
            try {
                val type = object : TypeToken<List<VoiceMode>>() {}.type
                gson.fromJson(modesJson, type)
            } catch (e: Exception) {
                getDefaultModes()
            }
        }
    }

    suspend fun saveVoiceModes(modes: List<VoiceMode>) {
        val modesJson = gson.toJson(modes)
        dataStore.edit { preferences ->
            preferences[VOICE_MODES_KEY] = modesJson
        }
    }

    suspend fun addVoiceMode(mode: VoiceMode) {
        dataStore.edit { preferences ->
            val currentModesJson = preferences[VOICE_MODES_KEY]
            val currentModes = if (currentModesJson.isNullOrEmpty()) {
                getDefaultModes()
            } else {
                val type = object : TypeToken<List<VoiceMode>>() {}.type
                gson.fromJson<List<VoiceMode>>(currentModesJson, type)
            }
            val updatedModes = currentModes + mode
            preferences[VOICE_MODES_KEY] = gson.toJson(updatedModes)
        }
    }

    suspend fun deleteVoiceMode(modeId: String) {
        dataStore.edit { preferences ->
            val currentModesJson = preferences[VOICE_MODES_KEY]
            if (!currentModesJson.isNullOrEmpty()) {
                val type = object : TypeToken<List<VoiceMode>>() {}.type
                val currentModes = gson.fromJson<List<VoiceMode>>(currentModesJson, type)
                val updatedModes = currentModes.filter { it.id != modeId }
                preferences[VOICE_MODES_KEY] = gson.toJson(updatedModes)
            }
        }
    }

    /**
     * Selected Mode
     */
    val selectedMode: Flow<String> = dataStore.data.map { preferences ->
        preferences[SELECTED_MODE_KEY] ?: "verbatim"
    }

    suspend fun setSelectedMode(modeId: String) {
        dataStore.edit { preferences ->
            preferences[SELECTED_MODE_KEY] = modeId
        }
    }

    /**
     * Default built-in modes
     */
    private fun getDefaultModes(): List<VoiceMode> = listOf(
        VoiceMode(
            id = "verbatim",
            name = "Verbatim",
            systemPrompt = "Transcribe the audio exactly as spoken.",
            isBuiltIn = true
        ),
        VoiceMode(
            id = "fix_grammar",
            name = "Fix Grammar",
            systemPrompt = "Transcribe this audio and fix any grammar, spelling, and punctuation errors while preserving the original meaning and tone.",
            isBuiltIn = true
        ),
        VoiceMode(
            id = "polite",
            name = "Polite",
            systemPrompt = "Transcribe this audio and rewrite it to be extremely polite and professional.",
            isBuiltIn = true
        ),
        VoiceMode(
            id = "casual",
            name = "Casual",
            systemPrompt = "Transcribe this audio and rewrite it in a casual, friendly tone.",
            isBuiltIn = true
        )
    )
}
