package com.hyperwhisper.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hyperwhisper.security.SecretSlot
import com.hyperwhisper.security.SecretsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing API configuration settings
 * Handles provider selection, API keys, model configuration, and language settings
 *
 * API keys (per-provider transcription keys and the LLM key) are persisted via
 * [SecretsRepository] (encrypted at rest). The DataStore here only carries
 * non-sensitive configuration. The legacy plaintext format is migrated on first
 * launch — see [migratePlaintextSecretsIfNeeded].
 */
@Singleton
class ApiSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson,
    private val secretsRepository: SecretsRepository,
    scope: CoroutineScope,
) {
    @Inject constructor(
        @ApiSettingsDataStore dataStore: DataStore<Preferences>,
        gson: Gson,
        secretsRepository: SecretsRepository,
    ) : this(dataStore, gson, secretsRepository, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    companion object {
        private val API_PROVIDER_KEY = stringPreferencesKey("api_provider")
        private val BASE_URL_KEY = stringPreferencesKey("base_url")
        private val PROVIDER_CONFIGS_KEY = stringPreferencesKey("provider_configs")
        private val MODEL_ID_KEY = stringPreferencesKey("model_id")
        private val INPUT_LANGUAGE_KEY = stringPreferencesKey("input_language")
        private val OUTPUT_LANGUAGE_KEY = stringPreferencesKey("output_language")
        private val LLM_CONFIG_KEY = stringPreferencesKey("llm_config")
        private val LOCAL_MODEL_SETTINGS_KEY = stringPreferencesKey("local_model_settings")

        // Legacy plaintext keys — read once during migration, then deleted.
        private val LEGACY_API_KEYS_MAP_KEY = stringPreferencesKey("api_keys_map")
        private val LEGACY_API_KEY_KEY = stringPreferencesKey("api_key")

        // Sentinel marking that plaintext → encrypted migration has run.
        private val SECRETS_MIGRATED_V1 = booleanPreferencesKey("secrets_migrated_v1")
    }

    /**
     * API Settings Flow — combines persisted config with decrypted secrets so
     * existing call sites that read `apiSettings.apiKeys[provider]` keep working
     * without any change.
     */
    val apiSettings: Flow<ApiSettings> = combine(
        dataStore.data,
        secretsRepository.secrets,
    ) { preferences, secrets ->
        buildApiSettings(preferences, secrets)
    }

    private val _apiSettingsState = MutableStateFlow(ApiSettings())

    /**
     * Synchronous, always-readable snapshot of the latest persisted settings.
     * Populated from DataStore as soon as the first emission arrives (typically
     * within milliseconds of app start). Used by interceptors and DI providers
     * that cannot safely block on a Flow.
     */
    val apiSettingsState: StateFlow<ApiSettings> = _apiSettingsState.asStateFlow()

    /** Synchronous accessor for the cached snapshot. Returns defaults until primed. */
    fun snapshot(): ApiSettings = _apiSettingsState.value

    init {
        scope.launch {
            migratePlaintextSecretsIfNeeded()
            apiSettings.collect { _apiSettingsState.value = it }
        }
    }

    private fun buildApiSettings(
        preferences: Preferences,
        secrets: Map<String, String>,
    ): ApiSettings {
        val provider = preferences[API_PROVIDER_KEY]?.let { ApiProvider.valueOf(it) }
            ?: ApiProvider.OPENAI

        // Build apiKeys map from decrypted secrets, keyed by ApiProvider.
        val apiKeysMap: Map<ApiProvider, String> = ApiProvider.entries.mapNotNull { p ->
            val key = secrets[SecretSlot.Provider(p.name).storageKey] ?: return@mapNotNull null
            if (key.isEmpty()) null else p to key
        }.toMap()

        val providerConfigs = try {
            val json = preferences[PROVIDER_CONFIGS_KEY]
            if (json.isNullOrEmpty()) {
                emptyMap()
            } else {
                val type = object : TypeToken<Map<String, ProviderConfig>>() {}.type
                val stringMap: Map<String, ProviderConfig> = gson.fromJson(json, type)
                stringMap.mapKeys { ApiProvider.valueOf(it.key) }
            }
        } catch (e: Exception) {
            emptyMap()
        }

        val llmConfig = try {
            val json = preferences[LLM_CONFIG_KEY]
            val parsed = if (json.isNullOrEmpty()) LlmConfig() else gson.fromJson(json, LlmConfig::class.java)
            // The persisted JSON never contains `apiKey` going forward — sourced
            // from SecretsRepository instead.
            parsed.copy(apiKey = secrets[SecretSlot.Llm.storageKey].orEmpty())
        } catch (e: Exception) {
            LlmConfig(apiKey = secrets[SecretSlot.Llm.storageKey].orEmpty())
        }

        val localModelSettings = try {
            val json = preferences[LOCAL_MODEL_SETTINGS_KEY]
            if (json.isNullOrEmpty()) LocalModelSettings()
            else gson.fromJson(json, LocalModelSettings::class.java)
        } catch (e: Exception) {
            LocalModelSettings()
        }

        return ApiSettings(
            provider = provider,
            baseUrl = preferences[BASE_URL_KEY] ?: provider.defaultEndpoint,
            apiKeys = apiKeysMap,
            providerConfigs = providerConfigs,
            modelId = preferences[MODEL_ID_KEY] ?: provider.defaultModels.firstOrNull() ?: "whisper-1",
            inputLanguage = preferences[INPUT_LANGUAGE_KEY] ?: "",
            outputLanguage = preferences[OUTPUT_LANGUAGE_KEY] ?: "",
            llmConfig = llmConfig,
            localModelSettings = localModelSettings,
        )
    }

    /**
     * One-shot migration from plaintext-in-DataStore → encrypted SecretsRepository.
     *
     * Order is chosen so a process kill at any point leaves a recoverable state:
     *  1. write plaintext into encrypted store (idempotent on re-run)
     *  2. strip plaintext keys from DataStore
     *  3. set sentinel
     *
     * If interrupted between (1) and (2), next boot re-runs the migration:
     * SecretsRepository overwrites with the same plaintext (different ciphertext,
     * same logical mapping) and proceeds. A no-op once the sentinel is set.
     */
    private suspend fun migratePlaintextSecretsIfNeeded() {
        val current = dataStore.data.first()
        if (current[SECRETS_MIGRATED_V1] == true) return

        val pendingSecrets = mutableMapOf<SecretSlot, String>()

        // 1a. Per-provider keys map (legacy single-key fallback included).
        val legacyMapJson = current[LEGACY_API_KEYS_MAP_KEY]
        val legacySingleKey = current[LEGACY_API_KEY_KEY]
        val legacyProvider = current[API_PROVIDER_KEY]?.let {
            runCatching { ApiProvider.valueOf(it) }.getOrNull()
        } ?: ApiProvider.OPENAI
        val legacyKeysMap: Map<ApiProvider, String> = if (!legacyMapJson.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val stringMap: Map<String, String> = gson.fromJson(legacyMapJson, type)
                stringMap.mapKeys { ApiProvider.valueOf(it.key) }
            } catch (_: Exception) {
                emptyMap()
            }
        } else if (!legacySingleKey.isNullOrEmpty()) {
            mapOf(legacyProvider to legacySingleKey)
        } else {
            emptyMap()
        }
        legacyKeysMap.forEach { (provider, key) ->
            if (key.isNotEmpty()) {
                pendingSecrets[SecretSlot.Provider(provider.name)] = key
            }
        }

        // 1b. LLM key buried inside llm_config JSON.
        val llmJson = current[LLM_CONFIG_KEY]
        val parsedLlm = try {
            if (llmJson.isNullOrEmpty()) null else gson.fromJson(llmJson, LlmConfig::class.java)
        } catch (_: Exception) {
            null
        }
        val llmPlainKey = parsedLlm?.apiKey.orEmpty()
        if (llmPlainKey.isNotEmpty()) {
            pendingSecrets[SecretSlot.Llm] = llmPlainKey
        }

        // 1. Write to encrypted store first.
        if (pendingSecrets.isNotEmpty()) {
            secretsRepository.putAll(pendingSecrets)
        }

        // 2 + 3. Strip plaintext + set sentinel + scrub llm_config.apiKey.
        dataStore.edit { prefs ->
            prefs.remove(LEGACY_API_KEYS_MAP_KEY)
            prefs.remove(LEGACY_API_KEY_KEY)
            if (parsedLlm != null && llmPlainKey.isNotEmpty()) {
                prefs[LLM_CONFIG_KEY] = gson.toJson(parsedLlm.copy(apiKey = ""))
            }
            prefs[SECRETS_MIGRATED_V1] = true
        }
    }

    /**
     * Save complete API settings configuration. API keys (per-provider and the
     * LLM key inside [ApiSettings.llmConfig]) are routed to [SecretsRepository];
     * everything else lands in DataStore.
     */
    suspend fun saveApiSettings(settings: ApiSettings) {
        // Route secrets first so the snapshot is consistent for any reader that
        // reacts to the DataStore write.
        val newSecrets: Map<SecretSlot, String> = buildMap {
            settings.apiKeys.forEach { (provider, key) ->
                put(SecretSlot.Provider(provider.name), key)
            }
            put(SecretSlot.Llm, settings.llmConfig.apiKey)
        }
        secretsRepository.putAll(newSecrets)

        dataStore.edit { preferences ->
            preferences[API_PROVIDER_KEY] = settings.provider.name

            val normalizedUrl = if (settings.baseUrl.isNotEmpty() && !settings.baseUrl.endsWith("/")) {
                settings.baseUrl + "/"
            } else {
                settings.baseUrl
            }
            preferences[BASE_URL_KEY] = normalizedUrl

            val configsStringMap = settings.providerConfigs.mapKeys { it.key.name }
            preferences[PROVIDER_CONFIGS_KEY] = gson.toJson(configsStringMap)

            // Persist llmConfig WITHOUT the apiKey field — kept in SecretsRepository.
            preferences[LLM_CONFIG_KEY] = gson.toJson(settings.llmConfig.copy(apiKey = ""))

            preferences[LOCAL_MODEL_SETTINGS_KEY] = gson.toJson(settings.localModelSettings)

            preferences[MODEL_ID_KEY] = settings.modelId
            preferences[INPUT_LANGUAGE_KEY] = settings.inputLanguage
            preferences[OUTPUT_LANGUAGE_KEY] = settings.outputLanguage
        }
    }

    /** Update local model configuration */
    suspend fun updateLocalModelSettings(settings: LocalModelSettings) {
        dataStore.edit { preferences ->
            preferences[LOCAL_MODEL_SETTINGS_KEY] = gson.toJson(settings)
        }
    }

    /** Update API key for a specific provider without affecting other settings */
    suspend fun updateProviderApiKey(provider: ApiProvider, apiKey: String) {
        secretsRepository.put(SecretSlot.Provider(provider.name), apiKey)
    }

    /** Update configuration for a specific provider */
    suspend fun updateProviderConfig(provider: ApiProvider, customBaseUrl: String, requiresAuth: Boolean) {
        dataStore.edit { preferences ->
            val currentSettings = apiSettings.first()
            val updatedConfigs = currentSettings.providerConfigs.toMutableMap()

            val normalizedUrl = if (customBaseUrl.isNotEmpty() && !customBaseUrl.endsWith("/")) {
                customBaseUrl + "/"
            } else {
                customBaseUrl
            }

            updatedConfigs[provider] = ProviderConfig(
                customBaseUrl = normalizedUrl,
                requiresAuth = requiresAuth,
            )

            val stringMap = updatedConfigs.mapKeys { it.key.name }
            preferences[PROVIDER_CONFIGS_KEY] = gson.toJson(stringMap)
        }
    }

    /** Update LLM configuration for post-processing */
    suspend fun updateLlmConfig(llmConfig: LlmConfig) {
        // Split: plaintext apiKey to SecretsRepository, rest to DataStore.
        secretsRepository.put(SecretSlot.Llm, llmConfig.apiKey)
        dataStore.edit { preferences ->
            preferences[LLM_CONFIG_KEY] = gson.toJson(llmConfig.copy(apiKey = ""))
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
        }
    }

    /** Set input language for speech recognition */
    suspend fun setInputLanguage(languageCode: String) {
        dataStore.edit { preferences ->
            preferences[INPUT_LANGUAGE_KEY] = languageCode
        }
    }

    /** Set output language for translation */
    suspend fun setOutputLanguage(languageCode: String) {
        dataStore.edit { preferences ->
            preferences[OUTPUT_LANGUAGE_KEY] = languageCode
        }
    }
}
