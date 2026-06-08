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

        const val CONFIGURATION_MODE_ID = "configuration"

        /**
         * The configuration mode's REAL prompt is generated at processing time
         * by ConfigPromptBuilder (it must embed the live settings), so the
         * stored prompt is only a placeholder shown in the Voice Modes editor.
         * Older builds persisted a 100-line static prompt; [normalizeModes]
         * replaces it at read time so it can never go stale.
         */
        const val CONFIGURATION_PROMPT_PLACEHOLDER =
            "This mode's prompt is generated at runtime from your current settings — " +
                "it always documents every available option and its current value. " +
                "Editing this text has no effect."
    }

    /**
     * Voice Modes Flow
     * Emits list of available voice modes with automatic fallback to defaults
     */
    val voiceModes: Flow<List<VoiceMode>> = dataStore.data.map { preferences ->
        val modesJson = preferences[VOICE_MODES_KEY]
        val modes: List<VoiceMode> = if (modesJson.isNullOrEmpty()) {
            getDefaultModes()
        } else {
            try {
                val type = object : TypeToken<List<VoiceMode>>() {}.type
                gson.fromJson(modesJson, type)
            } catch (e: Exception) {
                getDefaultModes()
            }
        }
        normalizeModes(modes)
    }

    /**
     * The configuration mode's stored prompt is irrelevant (generated at
     * runtime) — normalize it on every read so users who persisted the old
     * static prompt see the placeholder instead.
     */
    private fun normalizeModes(modes: List<VoiceMode>): List<VoiceMode> = modes.map { mode ->
        if (mode.id == CONFIGURATION_MODE_ID && mode.systemPrompt != CONFIGURATION_PROMPT_PLACEHOLDER) {
            mode.copy(systemPrompt = CONFIGURATION_PROMPT_PLACEHOLDER)
        } else {
            mode
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
            description = "Transcribe your speech exactly as spoken without any modifications. Perfect for dictation and capturing your exact words.",
            systemPrompt = "Transcribe the audio exactly as spoken.",
            model = "whisper-1",
            processingMode = "direct",
            isBuiltIn = false
        ),
        VoiceMode(
            id = "fix_grammar",
            name = "Fix Grammar",
            description = "Automatically correct grammar, spelling, and punctuation errors while preserving your original meaning and tone.",
            systemPrompt = "Transcribe this audio and fix any grammar, spelling, and punctuation errors while preserving the original meaning and tone.",
            model = "gpt-4o-mini",
            processingMode = "two-step",
            isBuiltIn = false
        ),
        VoiceMode(
            id = "polite",
            name = "Polite",
            description = "Transform your casual speech into polite, professional communication. Great for work emails and formal messages.",
            systemPrompt = "Transcribe this audio and reformulate it to be socially acceptable, polite, and conversational. Make it slightly better than neutral tone - professional yet friendly. Remove any harsh language or potential insults while preserving the core message and intent.",
            model = "gpt-4o-mini",
            processingMode = "two-step",
            isBuiltIn = false
        ),
        VoiceMode(
            id = "prompt_formatter",
            name = "Prompt Formatter",
            description = "Convert your voice input into clear, effective prompts optimized for AI assistants. Makes your requests more precise and actionable.",
            systemPrompt = "Reformulate the user's input into a clear, effective prompt suitable for LLM processing. Enhance clarity, add necessary context, and structure it for optimal AI understanding. Maintain the user's intent while making it more precise and actionable.",
            model = "gpt-4o-mini",
            processingMode = "two-step",
            isBuiltIn = false
        ),
        VoiceMode(
            id = "llm_response",
            name = "LLM Response",
            description = "Ask questions and get direct answers. Your speech is interpreted as a question and you receive a concise answer.",
            systemPrompt = "The user is asking a question. Provide a direct, concise answer to the question without any additional explanation or context. Return ONLY the answer itself.",
            model = "gpt-4o-mini",
            processingMode = "two-step",
            isBuiltIn = false
        ),
        VoiceMode(
            id = CONFIGURATION_MODE_ID,
            name = "Configuration",
            description = "Control keyboard settings with your voice. Say things like 'change language to Spanish', 'use the ocean theme', or 'switch keyboard to code'. Changes are shown for confirmation before being applied.",
            systemPrompt = CONFIGURATION_PROMPT_PLACEHOLDER,
            model = "gpt-4o-mini",
            processingMode = "two-step",
            isBuiltIn = false
        )
    )
}
