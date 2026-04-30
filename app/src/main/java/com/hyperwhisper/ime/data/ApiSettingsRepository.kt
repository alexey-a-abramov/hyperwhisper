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

private val Context.apiDataStore: DataStore<Preferences> by preferencesDataStore(name = "hyperwhisper_api_settings")

/**
 * Repository for managing API configuration settings
 * Handles provider selection, API keys, model configuration, and language settings
 */
@Singleton
class ApiSettingsRepository @Inject constructor(
    private val context: Context,
    private val gson: Gson
) {
    private val dataStore = context.apiDataStore

    companion object {
        private val API_PROVIDER_KEY = stringPreferencesKey("api_provider")
        private val BASE_URL_KEY = stringPreferencesKey("base_url")
        private val API_KEYS_MAP_KEY = stringPreferencesKey("api_keys_map") // Per-provider API keys as JSON
        private val PROVIDER_CONFIGS_KEY = stringPreferencesKey("provider_configs") // Per-provider configs as JSON
        private val MODEL_ID_KEY = stringPreferencesKey("model_id")
        private val INPUT_LANGUAGE_KEY = stringPreferencesKey("input_language")
        private val OUTPUT_LANGUAGE_KEY = stringPreferencesKey("output_language")
        private val LLM_CONFIG_KEY = stringPreferencesKey("llm_config") // LLM configuration as JSON
        private val LOCAL_MODEL_SETTINGS_KEY = stringPreferencesKey("local_model_settings") // Local model settings as JSON

        // Legacy key for migration from single-key version
        private val API_KEY_KEY = stringPreferencesKey("api_key")
    }

    /**
     * API Settings Flow
     * Emits current API configuration with automatic migration from legacy storage
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

        // Parse provider configs
        val providerConfigs = try {
            val json = preferences[PROVIDER_CONFIGS_KEY]
            if (json.isNullOrEmpty()) {
                emptyMap()
            } else {
                val type = object : TypeToken<Map<String, ProviderConfig>>() {}.type
                val stringMap: Map<String, ProviderConfig> = gson.fromJson(json, type)
                // Convert String keys to ApiProvider enum
                stringMap.mapKeys { ApiProvider.valueOf(it.key) }
            }
        } catch (e: Exception) {
            emptyMap()
        }

        // Parse LLM config
        val llmConfig = try {
            val json = preferences[LLM_CONFIG_KEY]
            if (json.isNullOrEmpty()) {
                LlmConfig() // Default
            } else {
                gson.fromJson(json, LlmConfig::class.java)
            }
        } catch (e: Exception) {
            LlmConfig() // Default
        }

        // Parse local model settings
        val localModelSettings = try {
            val json = preferences[LOCAL_MODEL_SETTINGS_KEY]
            if (json.isNullOrEmpty()) {
                LocalModelSettings() // Default
            } else {
                gson.fromJson(json, LocalModelSettings::class.java)
            }
        } catch (e: Exception) {
            LocalModelSettings() // Default
        }

        ApiSettings(
            provider = provider,
            baseUrl = preferences[BASE_URL_KEY] ?: provider.defaultEndpoint,
            apiKeys = apiKeysMap,
            providerConfigs = providerConfigs,
            modelId = preferences[MODEL_ID_KEY] ?: provider.defaultModels.firstOrNull() ?: "whisper-1",
            inputLanguage = preferences[INPUT_LANGUAGE_KEY] ?: "",
            outputLanguage = preferences[OUTPUT_LANGUAGE_KEY] ?: "",
            llmConfig = llmConfig,
            localModelSettings = localModelSettings
        )
    }

    /**
     * Save complete API settings configuration
     * Normalizes base URL to ensure it ends with /
     */
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

            // Save provider configs
            val configsStringMap = settings.providerConfigs.mapKeys { it.key.name }
            preferences[PROVIDER_CONFIGS_KEY] = gson.toJson(configsStringMap)

            // Save LLM config
            preferences[LLM_CONFIG_KEY] = gson.toJson(settings.llmConfig)

            // Save local model settings
            preferences[LOCAL_MODEL_SETTINGS_KEY] = gson.toJson(settings.localModelSettings)

            preferences[MODEL_ID_KEY] = settings.modelId
            preferences[INPUT_LANGUAGE_KEY] = settings.inputLanguage
            preferences[OUTPUT_LANGUAGE_KEY] = settings.outputLanguage
        }
    }

    /**
     * Update local model configuration
     */
    suspend fun updateLocalModelSettings(settings: LocalModelSettings) {
        dataStore.edit { preferences ->
            preferences[LOCAL_MODEL_SETTINGS_KEY] = gson.toJson(settings)
        }
    }

    /**
     * Update API key for a specific provider without affecting other settings
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
     * Update configuration for a specific provider
     */
    suspend fun updateProviderConfig(provider: ApiProvider, customBaseUrl: String, requiresAuth: Boolean) {
        dataStore.edit { preferences ->
            val currentSettings = apiSettings.first()
            val updatedConfigs = currentSettings.providerConfigs.toMutableMap()

            // Normalize URL to end with /
            val normalizedUrl = if (customBaseUrl.isNotEmpty() && !customBaseUrl.endsWith("/")) {
                customBaseUrl + "/"
            } else {
                customBaseUrl
            }

            updatedConfigs[provider] = ProviderConfig(
                customBaseUrl = normalizedUrl,
                requiresAuth = requiresAuth
            )

            val stringMap = updatedConfigs.mapKeys { it.key.name }
            preferences[PROVIDER_CONFIGS_KEY] = gson.toJson(stringMap)
        }
    }

    /**
     * Update LLM configuration for post-processing
     */
    suspend fun updateLlmConfig(llmConfig: LlmConfig) {
        dataStore.edit { preferences ->
            preferences[LLM_CONFIG_KEY] = gson.toJson(llmConfig)
        }
    }

    /**
     * Reset API settings to provider defaults
     * Preserves API key and language settings
     */
    suspend fun resetToDefaults(provider: ApiProvider) {
        dataStore.edit { preferences ->
            preferences[BASE_URL_KEY] = provider.defaultEndpoint
            preferences[MODEL_ID_KEY] = provider.defaultModels.firstOrNull() ?: "whisper-1"
            // Keep API key and language as-is
        }
    }

    /**
     * Set input language for speech recognition
     */
    suspend fun setInputLanguage(languageCode: String) {
        dataStore.edit { preferences ->
            preferences[INPUT_LANGUAGE_KEY] = languageCode
        }
    }

    /**
     * Set output language for translation
     */
    suspend fun setOutputLanguage(languageCode: String) {
        dataStore.edit { preferences ->
            preferences[OUTPUT_LANGUAGE_KEY] = languageCode
        }
    }
}
