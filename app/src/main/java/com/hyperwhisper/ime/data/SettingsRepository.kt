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
        private val APPEARANCE_DARK_MODE_KEY = stringPreferencesKey("appearance_dark_mode")
        private val APPEARANCE_UI_LANGUAGE_KEY = stringPreferencesKey("appearance_ui_language")
        private val APPEARANCE_UI_SCALE_KEY = stringPreferencesKey("appearance_ui_scale")
        private val APPEARANCE_FONT_FAMILY_KEY = stringPreferencesKey("appearance_font_family")
        private val APPEARANCE_AUTO_COPY_KEY = booleanPreferencesKey("appearance_auto_copy")
        private val APPEARANCE_ENABLE_HISTORY_KEY = booleanPreferencesKey("appearance_enable_history")
        private val APPEARANCE_TECHIE_MODE_KEY = booleanPreferencesKey("appearance_techie_mode")
        private val APPEARANCE_SHOW_KEYBOARD_SWITCHER_KEY = booleanPreferencesKey("appearance_show_keyboard_switcher")
        private val APPEARANCE_MAX_HISTORY_ITEMS_KEY = stringPreferencesKey("appearance_max_history_items")
        private val APPEARANCE_UNLIMITED_HISTORY_KEY = booleanPreferencesKey("appearance_unlimited_history")

        // Transcription history key
        private val TRANSCRIPTION_HISTORY_KEY = stringPreferencesKey("transcription_history")

        // Usage statistics key
        private val USAGE_STATISTICS_KEY = stringPreferencesKey("usage_statistics")

        // Cumulative audio duration key (total seconds of audio processed)
        private val CUMULATIVE_AUDIO_DURATION_KEY = stringPreferencesKey("cumulative_audio_duration")

        // Recently used languages key
        private val RECENTLY_USED_LANGUAGES_KEY = stringPreferencesKey("recently_used_languages")

        // Legacy key for migration
        private val API_KEY_KEY = stringPreferencesKey("api_key")

        private const val MAX_HISTORY_ITEMS = 20
        private const val MAX_RECENT_LANGUAGES = 5
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

    suspend fun updateVoiceMode(mode: VoiceMode) {
        dataStore.edit { preferences ->
            val currentModesJson = preferences[VOICE_MODES_KEY]
            val currentModes = if (currentModesJson.isNullOrEmpty()) {
                getDefaultModes()
            } else {
                val type = object : TypeToken<List<VoiceMode>>() {}.type
                gson.fromJson<List<VoiceMode>>(currentModesJson, type)
            }
            val updatedModes = currentModes.map {
                if (it.id == mode.id) mode else it
            }
            preferences[VOICE_MODES_KEY] = gson.toJson(updatedModes)
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
                    ColorSchemeOption.OCEAN_DEEP
                }
            } ?: ColorSchemeOption.OCEAN_DEEP,
            useDynamicColor = preferences[APPEARANCE_USE_DYNAMIC_COLOR_KEY] ?: true,
            darkModePreference = preferences[APPEARANCE_DARK_MODE_KEY]?.let {
                try {
                    DarkModePreference.valueOf(it)
                } catch (e: Exception) {
                    DarkModePreference.SYSTEM
                }
            } ?: DarkModePreference.SYSTEM,
            uiLanguage = preferences[APPEARANCE_UI_LANGUAGE_KEY] ?: "en",
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
            enableHistoryPanel = preferences[APPEARANCE_ENABLE_HISTORY_KEY] ?: true,
            techieModeEnabled = preferences[APPEARANCE_TECHIE_MODE_KEY] ?: false,
            showKeyboardSwitcher = preferences[APPEARANCE_SHOW_KEYBOARD_SWITCHER_KEY] ?: false,
            maxHistoryItems = preferences[APPEARANCE_MAX_HISTORY_ITEMS_KEY]?.toIntOrNull() ?: 20,
            unlimitedHistory = preferences[APPEARANCE_UNLIMITED_HISTORY_KEY] ?: false
        )
    }

    suspend fun saveAppearanceSettings(settings: AppearanceSettings) {
        dataStore.edit { preferences ->
            preferences[APPEARANCE_COLOR_SCHEME_KEY] = settings.colorScheme.name
            preferences[APPEARANCE_USE_DYNAMIC_COLOR_KEY] = settings.useDynamicColor
            preferences[APPEARANCE_DARK_MODE_KEY] = settings.darkModePreference.name
            preferences[APPEARANCE_UI_LANGUAGE_KEY] = settings.uiLanguage
            preferences[APPEARANCE_UI_SCALE_KEY] = settings.uiScale.name
            preferences[APPEARANCE_FONT_FAMILY_KEY] = settings.fontFamily.name
            preferences[APPEARANCE_AUTO_COPY_KEY] = settings.autoCopyToClipboard
            preferences[APPEARANCE_ENABLE_HISTORY_KEY] = settings.enableHistoryPanel
            preferences[APPEARANCE_TECHIE_MODE_KEY] = settings.techieModeEnabled
            preferences[APPEARANCE_SHOW_KEYBOARD_SWITCHER_KEY] = settings.showKeyboardSwitcher
            preferences[APPEARANCE_MAX_HISTORY_ITEMS_KEY] = settings.maxHistoryItems.toString()
            preferences[APPEARANCE_UNLIMITED_HISTORY_KEY] = settings.unlimitedHistory
        }
    }

    /**
     * Recently Used Languages Flow
     */
    val recentlyUsedLanguages: Flow<List<String>> = dataStore.data.map { preferences ->
        val languagesJson = preferences[RECENTLY_USED_LANGUAGES_KEY]
        if (languagesJson.isNullOrEmpty()) {
            emptyList()
        } else {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson(languagesJson, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun trackLanguageUsage(languageCode: String) {
        // Don't track empty (auto-detect) or "en" (English) as they're always at the top
        if (languageCode.isEmpty() || languageCode == "en") {
            return
        }

        dataStore.edit { preferences ->
            val currentJson = preferences[RECENTLY_USED_LANGUAGES_KEY]
            val currentList = if (currentJson.isNullOrEmpty()) {
                emptyList()
            } else {
                try {
                    val type = object : TypeToken<List<String>>() {}.type
                    gson.fromJson<List<String>>(currentJson, type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }

            // Remove language if it already exists, then add it at the front
            val updatedList = (listOf(languageCode) + currentList.filter { it != languageCode })
                .take(MAX_RECENT_LANGUAGES)

            preferences[RECENTLY_USED_LANGUAGES_KEY] = gson.toJson(updatedList)
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

    suspend fun addToHistory(text: String, audioFilePath: String? = null) {
        // Allow empty text if there's an audio file (for failed transcriptions that can be retried)
        if (text.isBlank() && audioFilePath == null) return

        dataStore.edit { preferences ->
            // Get current appearance settings to determine max history items
            val maxHistoryItems = preferences[APPEARANCE_MAX_HISTORY_ITEMS_KEY]?.toIntOrNull() ?: 20
            val unlimitedHistory = preferences[APPEARANCE_UNLIMITED_HISTORY_KEY] ?: false

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

            // Add new item at the beginning with audio file path
            val newItem = TranscriptionHistoryItem(text = text, audioFilePath = audioFilePath)
            val updatedHistory = listOf(newItem) + currentHistory

            // Trim history if not unlimited
            val trimmedHistory = if (unlimitedHistory) {
                updatedHistory
            } else {
                updatedHistory.take(maxHistoryItems)
            }

            // Delete audio files for items that are being removed
            val removedItems = if (unlimitedHistory) {
                emptyList()
            } else {
                updatedHistory.drop(maxHistoryItems)
            }
            removedItems.forEach { item ->
                item.audioFilePath?.let { path ->
                    try {
                        java.io.File(path).delete()
                    } catch (e: Exception) {
                        // Ignore deletion errors
                    }
                }
            }

            preferences[TRANSCRIPTION_HISTORY_KEY] = gson.toJson(trimmedHistory)
        }
    }

    /**
     * Update an existing history item with new transcription text
     * Used when reprocessing audio with different settings
     */
    suspend fun updateHistoryItem(itemId: String, newText: String) {
        dataStore.edit { preferences ->
            val currentHistoryJson = preferences[TRANSCRIPTION_HISTORY_KEY]
            if (!currentHistoryJson.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<List<TranscriptionHistoryItem>>() {}.type
                    val currentHistory = gson.fromJson<List<TranscriptionHistoryItem>>(currentHistoryJson, type) ?: emptyList()

                    // Find and update the item
                    val updatedHistory = currentHistory.map { item ->
                        if (item.id == itemId) {
                            item.copy(text = newText, timestamp = System.currentTimeMillis())
                        } else {
                            item
                        }
                    }

                    preferences[TRANSCRIPTION_HISTORY_KEY] = gson.toJson(updatedHistory)
                } catch (e: Exception) {
                    // Ignore parsing errors
                }
            }
        }
    }

    suspend fun clearHistory() {
        dataStore.edit { preferences ->
            // Get current history to delete audio files
            val currentHistoryJson = preferences[TRANSCRIPTION_HISTORY_KEY]
            if (!currentHistoryJson.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<List<TranscriptionHistoryItem>>() {}.type
                    val currentHistory = gson.fromJson<List<TranscriptionHistoryItem>>(currentHistoryJson, type)

                    // Delete all audio files
                    currentHistory?.forEach { item ->
                        item.audioFilePath?.let { path ->
                            try {
                                java.io.File(path).delete()
                            } catch (e: Exception) {
                                // Ignore deletion errors
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore parsing errors
                }
            }

            preferences.remove(TRANSCRIPTION_HISTORY_KEY)
        }
    }

    /**
     * Check how many items will be deleted if history size is reduced
     * Returns number of items that will be deleted (0 if none)
     */
    suspend fun checkHistorySizeReduction(newMaxSize: Int, newUnlimited: Boolean): Int {
        if (newUnlimited) return 0 // Unlimited means nothing deleted

        val currentHistory = transcriptionHistory.first()
        val itemsToDelete = (currentHistory.size - newMaxSize).coerceAtLeast(0)
        return itemsToDelete
    }

    /**
     * Trim history to new size, deleting excess items and their audio files
     */
    suspend fun trimHistoryToSize(maxSize: Int) {
        dataStore.edit { preferences ->
            val currentHistoryJson = preferences[TRANSCRIPTION_HISTORY_KEY]
            if (!currentHistoryJson.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<List<TranscriptionHistoryItem>>() {}.type
                    val currentHistory = gson.fromJson<List<TranscriptionHistoryItem>>(currentHistoryJson, type) ?: emptyList()

                    // Keep only the most recent maxSize items
                    val trimmedHistory = currentHistory.take(maxSize)

                    // Delete audio files for removed items
                    val removedItems = currentHistory.drop(maxSize)
                    removedItems.forEach { item ->
                        item.audioFilePath?.let { path ->
                            try {
                                java.io.File(path).delete()
                            } catch (e: Exception) {
                                // Ignore deletion errors
                            }
                        }
                    }

                    preferences[TRANSCRIPTION_HISTORY_KEY] = gson.toJson(trimmedHistory)
                } catch (e: Exception) {
                    // Ignore parsing errors
                }
            }
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
     * Default modes - all editable for prompt database functionality
     */
    private fun getDefaultModes(): List<VoiceMode> = listOf(
        VoiceMode(
            id = "verbatim",
            name = "Verbatim",
            systemPrompt = "Transcribe the audio exactly as spoken.",
            isBuiltIn = false
        ),
        VoiceMode(
            id = "fix_grammar",
            name = "Fix Grammar",
            systemPrompt = "Transcribe this audio and fix any grammar, spelling, and punctuation errors while preserving the original meaning and tone.",
            isBuiltIn = false
        ),
        VoiceMode(
            id = "polite",
            name = "Polite",
            systemPrompt = "Transcribe this audio and reformulate it to be socially acceptable, polite, and conversational. Make it slightly better than neutral tone - professional yet friendly. Remove any harsh language or potential insults while preserving the core message and intent.",
            isBuiltIn = false
        ),
        VoiceMode(
            id = "prompt_formatter",
            name = "Prompt Formatter",
            systemPrompt = "Reformulate the user's input into a clear, effective prompt suitable for LLM processing. Enhance clarity, add necessary context, and structure it for optimal AI understanding. Maintain the user's intent while making it more precise and actionable.",
            isBuiltIn = false
        ),
        VoiceMode(
            id = "llm_response",
            name = "LLM Response",
            systemPrompt = "The user is asking a question. Provide a direct, concise answer to the question without any additional explanation or context. Return ONLY the answer itself.",
            isBuiltIn = false
        ),
        VoiceMode(
            id = "configuration",
            name = "Configuration",
            systemPrompt = """You are a voice command interpreter for HyperWhisper keyboard settings. Parse the user's spoken command and output ONLY a valid JSON object.

## Output Format
{
  "command": "change_setting",
  "setting": "SETTING_NAME",
  "value": "VALUE"
}

## Available Settings

### Language Settings
| Setting | Description | Values |
|---------|-------------|--------|
| input_language | Speech recognition language | Language codes ("en", "ru", "es", "zh", "ja", "de", "fr", "ar", "hi", etc.) or full names ("English", "Spanish", "Chinese", etc.) |
| output_language | Translation target language | Same as input_language |
| ui_language | Interface language | "en" (English), "ru" (Russian), "ar" (Arabic), or language names |

### Mode Settings
| Setting | Description | Values |
|---------|-------------|--------|
| voice_mode | Voice processing mode | "verbatim", "fix_grammar", "polite", "prompt_formatter", "llm_response", "configuration" |
| enable_configuration_mode | Toggle command mode | "true", "false", "on", "off", "enable", "disable" |

### Appearance Settings
| Setting | Description | Values |
|---------|-------------|--------|
| theme | App color theme | "system", "light", "dark", "auto" |

### Feature Toggles
| Setting | Description | Values |
|---------|-------------|--------|
| enable_history | Transcription history | "true", "false", "on", "off", "enable", "disable" |
| enable_techie_mode | Developer/debug mode | "true", "false", "on", "off", "enable", "disable" |

## Examples

User: "Change input language to Spanish"
{"command": "change_setting", "setting": "input_language", "value": "spanish"}

User: "I want to speak in Japanese"
{"command": "change_setting", "setting": "input_language", "value": "japanese"}

User: "Translate to French"
{"command": "change_setting", "setting": "output_language", "value": "french"}

User: "Switch to dark mode"
{"command": "change_setting", "setting": "theme", "value": "dark"}

User: "Use light theme"
{"command": "change_setting", "setting": "theme", "value": "light"}

User: "Follow system theme"
{"command": "change_setting", "setting": "theme", "value": "system"}

User: "Enable history"
{"command": "change_setting", "setting": "enable_history", "value": "true"}

User: "Turn off history"
{"command": "change_setting", "setting": "enable_history", "value": "false"}

User: "Change mode to verbatim"
{"command": "change_setting", "setting": "voice_mode", "value": "verbatim"}

User: "Use fix grammar mode"
{"command": "change_setting", "setting": "voice_mode", "value": "fix_grammar"}

User: "Switch to polite mode"
{"command": "change_setting", "setting": "voice_mode", "value": "polite"}

User: "Enable developer mode"
{"command": "change_setting", "setting": "enable_techie_mode", "value": "true"}

User: "Turn off techie mode"
{"command": "change_setting", "setting": "enable_techie_mode", "value": "false"}

User: "Exit configuration mode"
{"command": "change_setting", "setting": "enable_configuration_mode", "value": "false"}

User: "Turn off command mode"
{"command": "change_setting", "setting": "enable_configuration_mode", "value": "false"}

User: "Change interface to Russian"
{"command": "change_setting", "setting": "ui_language", "value": "russian"}

User: "Set UI language to Arabic"
{"command": "change_setting", "setting": "ui_language", "value": "arabic"}

## Important Rules
1. Output ONLY the JSON object, no explanations or additional text
2. Use lowercase for setting names and values
3. For languages, accept both codes and full names
4. For boolean settings, normalize to "true" or "false"
5. Match user intent even if phrasing varies (e.g., "dark theme" = "dark mode")""",
            isBuiltIn = false
        )
    )
}
