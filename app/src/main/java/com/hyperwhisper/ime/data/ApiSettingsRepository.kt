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
import kotlinx.coroutines.runBlocking
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
        private val LLM_PROVIDER_CONFIGS_KEY = stringPreferencesKey("llm_provider_configs")
        private val LOCAL_MODEL_SETTINGS_KEY = stringPreferencesKey("local_model_settings")
        private val LAST_TESTED_AT_KEY = stringPreferencesKey("last_tested_at")
        private val LLM_LAST_TESTED_AT_KEY = stringPreferencesKey("llm_last_tested_at")

        // Legacy plaintext keys — read once during migration, then deleted.
        private val LEGACY_API_KEYS_MAP_KEY = stringPreferencesKey("api_keys_map")
        private val LEGACY_API_KEY_KEY = stringPreferencesKey("api_key")

        // Sentinel marking that plaintext → encrypted migration has run.
        private val SECRETS_MIGRATED_V1 = booleanPreferencesKey("secrets_migrated_v1")

        // Sentinel marking that the legacy single-LLM-key has been promoted
        // into the per-provider [SecretSlot.LlmProvider] map.
        private val LLM_PER_PROVIDER_KEYS_MIGRATED_V1 =
            booleanPreferencesKey("llm_per_provider_keys_migrated_v1")
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

    private val _isLoaded = MutableStateFlow(false)

    /**
     * True once [apiSettingsState] reflects a real DataStore emission rather
     * than the placeholder defaults it is seeded with. Writers that merge into
     * the current settings must wait for this (see [awaitLoaded]) or they can
     * persist the placeholder and wipe stored configuration.
     */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    /** Suspends until the first real DataStore emission has been applied. */
    suspend fun awaitLoaded() {
        _isLoaded.first { it }
    }

    /**
     * Synchronous accessor for the cached snapshot. If the internal collector
     * hasn't primed the cache yet, this blocks on a direct first read of the
     * persisted settings instead of serving placeholder defaults — network
     * interceptors and DI providers must never observe an empty-keys
     * [ApiSettings].
     */
    fun snapshot(): ApiSettings =
        if (_isLoaded.value) {
            _apiSettingsState.value
        } else {
            runBlocking { apiSettings.first() }
        }

    init {
        scope.launch {
            migratePlaintextSecretsIfNeeded()
            migrateLlmKeyToPerProviderIfNeeded()
            apiSettings.collect {
                _apiSettingsState.value = it
                _isLoaded.value = true
            }
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

        val llmProviderConfigs = try {
            val json = preferences[LLM_PROVIDER_CONFIGS_KEY]
            if (json.isNullOrEmpty()) {
                emptyMap()
            } else {
                val type = object : TypeToken<Map<String, LlmProviderConfig>>() {}.type
                val stringMap: Map<String, LlmProviderConfig> = gson.fromJson(json, type)
                stringMap.mapKeys { LlmProvider.valueOf(it.key) }
            }
        } catch (e: Exception) {
            emptyMap()
        }

        val llmApiKeysMap: Map<LlmProvider, String> = LlmProvider.entries.mapNotNull { p ->
            val key = secrets[SecretSlot.LlmProvider(p.name).storageKey] ?: return@mapNotNull null
            if (key.isEmpty()) null else p to key
        }.toMap()

        val llmConfig = try {
            val json = preferences[LLM_CONFIG_KEY]
            val parsed = if (json.isNullOrEmpty()) LlmConfig() else gson.fromJson(json, LlmConfig::class.java)
            // The persisted JSON never carries `apiKey`, `apiKeys`, or
            // `providerConfigs` going forward — those live in SecretsRepository
            // and the LLM_PROVIDER_CONFIGS_KEY prefs entry respectively. Mirror
            // the active provider's key into the legacy [apiKey] field so older
            // readers (LlmServiceFactory, ConnectionTester, VoiceRepository)
            // keep working without a refactor.
            val activeKey = llmApiKeysMap[parsed.provider]
                ?: secrets[SecretSlot.Llm.storageKey].orEmpty()
            val activeConfig = llmProviderConfigs[parsed.provider]
            parsed.copy(
                apiKey = activeKey,
                customBaseUrl = activeConfig?.customBaseUrl
                    ?.takeIf { it.isNotEmpty() } ?: parsed.customBaseUrl,
                requiresAuth = activeConfig?.requiresAuth ?: parsed.requiresAuth,
                apiKeys = llmApiKeysMap,
                providerConfigs = llmProviderConfigs,
            )
        } catch (e: Exception) {
            val fallbackProvider = LlmConfig().provider
            val fallbackKey = llmApiKeysMap[fallbackProvider]
                ?: secrets[SecretSlot.Llm.storageKey].orEmpty()
            LlmConfig(
                apiKey = fallbackKey,
                apiKeys = llmApiKeysMap,
                providerConfigs = llmProviderConfigs,
            )
        }

        val localModelSettings = try {
            val json = preferences[LOCAL_MODEL_SETTINGS_KEY]
            if (json.isNullOrEmpty()) LocalModelSettings()
            else gson.fromJson(json, LocalModelSettings::class.java)
        } catch (e: Exception) {
            LocalModelSettings()
        }

        val lastTestedAt = readLongMap<ApiProvider>(
            preferences[LAST_TESTED_AT_KEY],
        ) { ApiProvider.valueOf(it) }
        val llmLastTestedAt = readLongMap<LlmProvider>(
            preferences[LLM_LAST_TESTED_AT_KEY],
        ) { LlmProvider.valueOf(it) }

        return ApiSettings(
            provider = provider,
            baseUrl = preferences[BASE_URL_KEY] ?: provider.defaultEndpoint,
            apiKeys = apiKeysMap,
            providerConfigs = providerConfigs,
            modelId = preferences[MODEL_ID_KEY] ?: provider.defaultModels.firstOrNull() ?: "",
            inputLanguage = preferences[INPUT_LANGUAGE_KEY] ?: "",
            outputLanguage = preferences[OUTPUT_LANGUAGE_KEY] ?: "",
            llmConfig = llmConfig.copy(lastTestedAt = llmLastTestedAt),
            localModelSettings = localModelSettings,
            lastTestedAt = lastTestedAt,
        )
    }

    private inline fun <K> readLongMap(
        json: String?,
        keyOf: (String) -> K,
    ): Map<K, Long> = try {
        if (json.isNullOrEmpty()) emptyMap()
        else {
            val type = object : TypeToken<Map<String, Long>>() {}.type
            val raw: Map<String, Long> = gson.fromJson(json, type)
            raw.mapNotNull { (k, v) ->
                try {
                    keyOf(k) to v
                } catch (_: IllegalArgumentException) {
                    // Stored key isn't a known enum (e.g. provider removed in
                    // a later release) — silently drop rather than crash the
                    // settings flow.
                    null
                }
            }.toMap()
        }
    } catch (_: Exception) {
        emptyMap()
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
     * Promote the legacy single-LLM-key ([SecretSlot.Llm]) into the new
     * per-provider [SecretSlot.LlmProvider] slot for whichever LlmProvider
     * is currently active. The legacy slot is left in place — the read
     * path mirrors the active per-provider key back into it for code that
     * still reads it directly. Idempotent via the V1 sentinel.
     */
    private suspend fun migrateLlmKeyToPerProviderIfNeeded() {
        val current = dataStore.data.first()
        if (current[LLM_PER_PROVIDER_KEYS_MIGRATED_V1] == true) return

        val secretsSnapshot = secretsRepository.secrets.first()
        val legacyKey = secretsSnapshot[SecretSlot.Llm.storageKey].orEmpty()
        if (legacyKey.isNotEmpty()) {
            val activeLlmProvider = try {
                val json = current[LLM_CONFIG_KEY]
                if (json.isNullOrEmpty()) LlmConfig().provider
                else gson.fromJson(json, LlmConfig::class.java).provider
            } catch (_: Exception) {
                LlmConfig().provider
            }
            // Don't overwrite if a per-provider key was already stored for this
            // provider (e.g. set in a previous partial run).
            val existing = secretsSnapshot[
                SecretSlot.LlmProvider(activeLlmProvider.name).storageKey
            ].orEmpty()
            if (existing.isEmpty()) {
                secretsRepository.put(SecretSlot.LlmProvider(activeLlmProvider.name), legacyKey)
            }
        }

        dataStore.edit { prefs ->
            prefs[LLM_PER_PROVIDER_KEYS_MIGRATED_V1] = true
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
            settings.llmConfig.apiKeys.forEach { (provider, key) ->
                put(SecretSlot.LlmProvider(provider.name), key)
            }
            // Mirror the active LLM key into the legacy slot so any code that
            // still reads SecretSlot.Llm directly sees a consistent value.
            put(SecretSlot.Llm, settings.llmConfig.getCurrentApiKey())
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

            val llmConfigsStringMap = settings.llmConfig.providerConfigs.mapKeys { it.key.name }
            preferences[LLM_PROVIDER_CONFIGS_KEY] = gson.toJson(llmConfigsStringMap)

            // Persist llmConfig WITHOUT secret/per-provider fields — those live
            // in SecretsRepository and LLM_PROVIDER_CONFIGS_KEY respectively.
            preferences[LLM_CONFIG_KEY] = gson.toJson(
                settings.llmConfig.copy(
                    apiKey = "",
                    apiKeys = emptyMap(),
                    providerConfigs = emptyMap(),
                )
            )

            preferences[LOCAL_MODEL_SETTINGS_KEY] = gson.toJson(settings.localModelSettings)

            preferences[MODEL_ID_KEY] = settings.modelId
            preferences[INPUT_LANGUAGE_KEY] = settings.inputLanguage
            preferences[OUTPUT_LANGUAGE_KEY] = settings.outputLanguage

            writeLongMap(preferences, LAST_TESTED_AT_KEY, settings.lastTestedAt) { it.name }
            writeLongMap(preferences, LLM_LAST_TESTED_AT_KEY, settings.llmConfig.lastTestedAt) { it.name }
        }
    }

    private inline fun <K> writeLongMap(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        map: Map<K, Long>,
        nameOf: (K) -> String,
    ) {
        val stringMap = map.mapKeys { nameOf(it.key) }
        preferences[key] = gson.toJson(stringMap)
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
        // Split: per-provider keys → SecretsRepository, per-provider configs
        // and the active provider/model → DataStore. Mirror the active key
        // into the legacy slot for back-compat readers.
        val secretsToPut: Map<SecretSlot, String> = buildMap {
            llmConfig.apiKeys.forEach { (provider, key) ->
                put(SecretSlot.LlmProvider(provider.name), key)
            }
            put(SecretSlot.Llm, llmConfig.getCurrentApiKey())
        }
        secretsRepository.putAll(secretsToPut)
        dataStore.edit { preferences ->
            val llmConfigsStringMap = llmConfig.providerConfigs.mapKeys { it.key.name }
            preferences[LLM_PROVIDER_CONFIGS_KEY] = gson.toJson(llmConfigsStringMap)
            preferences[LLM_CONFIG_KEY] = gson.toJson(
                llmConfig.copy(
                    apiKey = "",
                    apiKeys = emptyMap(),
                    providerConfigs = emptyMap(),
                )
            )
        }
    }

    /** Update API key for a specific LLM provider without affecting other settings. */
    suspend fun updateLlmProviderApiKey(provider: LlmProvider, apiKey: String) {
        secretsRepository.put(SecretSlot.LlmProvider(provider.name), apiKey)
        // If the updated provider is the active one, refresh the legacy mirror
        // so directly-reading callers don't see the old key.
        val currentActive = apiSettings.first().llmConfig.provider
        if (currentActive == provider) {
            secretsRepository.put(SecretSlot.Llm, apiKey)
        }
    }

    /** Record a successful Settings-side test for a transcription provider.
     *  Timestamp is epoch-millis from [System.currentTimeMillis] for portable
     *  duration math (no calendar / TZ awareness). Failed / cancelled tests
     *  must not call this — staleness is meaningful only for last *success*. */
    suspend fun recordProviderTested(provider: ApiProvider, timestampMillis: Long) {
        dataStore.edit { preferences ->
            val current = readLongMap<ApiProvider>(preferences[LAST_TESTED_AT_KEY]) {
                ApiProvider.valueOf(it)
            }.toMutableMap()
            current[provider] = timestampMillis
            writeLongMap(preferences, LAST_TESTED_AT_KEY, current) { it.name }
        }
    }

    /** Mirror of [recordProviderTested] for the LLM post-processing side. */
    suspend fun recordLlmProviderTested(provider: LlmProvider, timestampMillis: Long) {
        dataStore.edit { preferences ->
            val current = readLongMap<LlmProvider>(preferences[LLM_LAST_TESTED_AT_KEY]) {
                LlmProvider.valueOf(it)
            }.toMutableMap()
            current[provider] = timestampMillis
            writeLongMap(preferences, LLM_LAST_TESTED_AT_KEY, current) { it.name }
        }
    }

    /** Update per-provider LLM configuration (custom base URL, requiresAuth override). */
    suspend fun updateLlmProviderConfig(
        provider: LlmProvider,
        customBaseUrl: String,
        requiresAuth: Boolean?,
    ) {
        dataStore.edit { preferences ->
            val current = apiSettings.first().llmConfig.providerConfigs.toMutableMap()
            val normalizedUrl = if (customBaseUrl.isNotEmpty() && !customBaseUrl.endsWith("/")) {
                customBaseUrl + "/"
            } else {
                customBaseUrl
            }
            current[provider] = LlmProviderConfig(
                customBaseUrl = normalizedUrl,
                requiresAuth = requiresAuth,
            )
            val stringMap = current.mapKeys { it.key.name }
            preferences[LLM_PROVIDER_CONFIGS_KEY] = gson.toJson(stringMap)
        }
    }

    /**
     * Reset API settings to provider defaults
     * Preserves API key and language settings
     */
    suspend fun resetToDefaults(provider: ApiProvider) {
        dataStore.edit { preferences ->
            preferences[BASE_URL_KEY] = provider.defaultEndpoint
            preferences[MODEL_ID_KEY] = provider.defaultModels.firstOrNull() ?: ""
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
