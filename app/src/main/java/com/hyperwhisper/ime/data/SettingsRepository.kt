package com.hyperwhisper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

        // Appearance settings keys
        private val APPEARANCE_COLOR_SCHEME_KEY = stringPreferencesKey("appearance_color_scheme")
        private val APPEARANCE_USE_DYNAMIC_COLOR_KEY = booleanPreferencesKey("appearance_use_dynamic_color")
        private val APPEARANCE_UI_SCALE_KEY = stringPreferencesKey("appearance_ui_scale")
        private val APPEARANCE_FONT_FAMILY_KEY = stringPreferencesKey("appearance_font_family")
        private val APPEARANCE_AUTO_COPY_KEY = booleanPreferencesKey("appearance_auto_copy")
        private val APPEARANCE_ENABLE_HISTORY_KEY = booleanPreferencesKey("appearance_enable_history")

        // Transcription history key
        private val TRANSCRIPTION_HISTORY_KEY = stringPreferencesKey("transcription_history")

        // Usage statistics key
        private val USAGE_STATISTICS_KEY = stringPreferencesKey("usage_statistics")

        // Legacy key for migration
        private val API_KEY_KEY = stringPreferencesKey("api_key")

        private const val MAX_HISTORY_ITEMS = 20
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
     * Appearance Settings Flow
     */
    val appearanceSettings: Flow<AppearanceSettings> = dataStore.data.map { preferences ->
        AppearanceSettings(
            colorScheme = preferences[APPEARANCE_COLOR_SCHEME_KEY]?.let {
                try {
                    ColorSchemeOption.valueOf(it)
                } catch (e: Exception) {
                    ColorSchemeOption.PURPLE
                }
            } ?: ColorSchemeOption.PURPLE,
            useDynamicColor = preferences[APPEARANCE_USE_DYNAMIC_COLOR_KEY] ?: true,
            uiScale = preferences[APPEARANCE_UI_SCALE_KEY]?.let {
                try {
                    UIScaleOption.valueOf(it)
                } catch (e: Exception) {
                    UIScaleOption.MEDIUM
                }
            } ?: UIScaleOption.MEDIUM,
            fontFamily = preferences[APPEARANCE_FONT_FAMILY_KEY]?.let {
                try {
                    FontFamilyOption.valueOf(it)
                } catch (e: Exception) {
                    FontFamilyOption.DEFAULT
                }
            } ?: FontFamilyOption.DEFAULT,
            autoCopyToClipboard = preferences[APPEARANCE_AUTO_COPY_KEY] ?: true,
            enableHistoryPanel = preferences[APPEARANCE_ENABLE_HISTORY_KEY] ?: true
        )
    }

    suspend fun saveAppearanceSettings(settings: AppearanceSettings) {
        dataStore.edit { preferences ->
            preferences[APPEARANCE_COLOR_SCHEME_KEY] = settings.colorScheme.name
            preferences[APPEARANCE_USE_DYNAMIC_COLOR_KEY] = settings.useDynamicColor
            preferences[APPEARANCE_UI_SCALE_KEY] = settings.uiScale.name
            preferences[APPEARANCE_FONT_FAMILY_KEY] = settings.fontFamily.name
            preferences[APPEARANCE_AUTO_COPY_KEY] = settings.autoCopyToClipboard
            preferences[APPEARANCE_ENABLE_HISTORY_KEY] = settings.enableHistoryPanel
        }
    }

    /**
     * Transcription History Management
     */
    val transcriptionHistory: Flow<List<TranscriptionHistoryItem>> = dataStore.data.map { preferences ->
        val historyJson = preferences[TRANSCRIPTION_HISTORY_KEY]
        if (historyJson.isNullOrEmpty()) {
            emptyList()
        } else {
            try {
                val type = object : TypeToken<List<TranscriptionHistoryItem>>() {}.type
                gson.fromJson(historyJson, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun addToHistory(text: String) {
        if (text.isBlank()) return

        dataStore.edit { preferences ->
            val currentHistoryJson = preferences[TRANSCRIPTION_HISTORY_KEY]
            val currentHistory = if (currentHistoryJson.isNullOrEmpty()) {
                emptyList()
            } else {
                try {
                    val type = object : TypeToken<List<TranscriptionHistoryItem>>() {}.type
                    gson.fromJson<List<TranscriptionHistoryItem>>(currentHistoryJson, type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }

            // Add new item at the beginning
            val newItem = TranscriptionHistoryItem(text = text)
            val updatedHistory = listOf(newItem) + currentHistory

            // Keep only last MAX_HISTORY_ITEMS items
            val trimmedHistory = updatedHistory.take(MAX_HISTORY_ITEMS)

            preferences[TRANSCRIPTION_HISTORY_KEY] = gson.toJson(trimmedHistory)
        }
    }

    suspend fun clearHistory() {
        dataStore.edit { preferences ->
            preferences.remove(TRANSCRIPTION_HISTORY_KEY)
        }
    }

    /**
     * Usage Statistics Management
     */
    val usageStatistics: Flow<UsageStatistics> = dataStore.data.map { preferences ->
        val statsJson = preferences[USAGE_STATISTICS_KEY]
        if (statsJson.isNullOrEmpty()) {
            UsageStatistics()
        } else {
            try {
                gson.fromJson(statsJson, UsageStatistics::class.java) ?: UsageStatistics()
            } catch (e: Exception) {
                UsageStatistics()
            }
        }
    }

    suspend fun recordUsage(
        modelId: String,
        inputTokens: Int,
        outputTokens: Int,
        totalTokens: Int,
        audioDurationSeconds: Double
    ) {
        dataStore.edit { preferences ->
            val currentStatsJson = preferences[USAGE_STATISTICS_KEY]
            val currentStats = if (currentStatsJson.isNullOrEmpty()) {
                UsageStatistics()
            } else {
                try {
                    gson.fromJson(currentStatsJson, UsageStatistics::class.java) ?: UsageStatistics()
                } catch (e: Exception) {
                    UsageStatistics()
                }
            }

            // Update model usage
            val currentModelUsage = currentStats.modelUsage[modelId] ?: ModelUsage()
            val newModelUsage = ModelUsage(
                inputTokens = currentModelUsage.inputTokens + inputTokens,
                outputTokens = currentModelUsage.outputTokens + outputTokens,
                totalTokens = currentModelUsage.totalTokens + totalTokens
            )

            val updatedModelUsage = currentStats.modelUsage.toMutableMap()
            updatedModelUsage[modelId] = newModelUsage

            // Update total audio seconds
            val updatedStats = UsageStatistics(
                modelUsage = updatedModelUsage,
                totalAudioSeconds = currentStats.totalAudioSeconds + audioDurationSeconds
            )

            preferences[USAGE_STATISTICS_KEY] = gson.toJson(updatedStats)
        }
    }

    suspend fun clearStatistics() {
        dataStore.edit { preferences ->
            preferences.remove(USAGE_STATISTICS_KEY)
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
        ),
        VoiceMode(
            id = "llm_response",
            name = "LLM Response",
            systemPrompt = "The user is asking a question. Provide a direct, concise answer to the question without any additional explanation or context. Return ONLY the answer itself.",
            isBuiltIn = true
        )
    )
}
