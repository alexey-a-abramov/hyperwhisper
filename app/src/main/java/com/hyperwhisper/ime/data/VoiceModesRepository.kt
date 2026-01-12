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
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.voiceModesDataStore: DataStore<Preferences> by preferencesDataStore(name = "hyperwhisper_voice_modes")

/**
 * Repository for managing voice modes and mode selection
 * Handles CRUD operations for voice modes and tracks the currently selected mode
 */
@Singleton
class VoiceModesRepository @Inject constructor(
    private val context: Context,
    private val gson: Gson
) {
    private val dataStore = context.voiceModesDataStore

    companion object {
        private val VOICE_MODES_KEY = stringPreferencesKey("voice_modes")
        private val SELECTED_MODE_KEY = stringPreferencesKey("selected_mode")
    }

    /**
     * Voice Modes Flow
     * Emits list of available voice modes with automatic fallback to defaults
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

    /**
     * Selected Mode Flow
     * Emits the ID of the currently selected mode (defaults to "verbatim")
     */
    val selectedMode: Flow<String> = dataStore.data.map { preferences ->
        preferences[SELECTED_MODE_KEY] ?: "verbatim"
    }

    /**
     * Save complete list of voice modes
     */
    suspend fun saveVoiceModes(modes: List<VoiceMode>) {
        val modesJson = gson.toJson(modes)
        dataStore.edit { preferences ->
            preferences[VOICE_MODES_KEY] = modesJson
        }
    }

    /**
     * Add a new voice mode to the list
     */
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

    /**
     * Delete a voice mode by ID
     */
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
     * Update an existing voice mode
     */
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
     * Set the currently selected voice mode
     */
    suspend fun setSelectedMode(modeId: String) {
        dataStore.edit { preferences ->
            preferences[SELECTED_MODE_KEY] = modeId
        }
    }

    /**
     * Get default voice modes
     * Includes verbatim, grammar correction, polite reformulation, prompt formatting,
     * LLM response, and configuration modes
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
