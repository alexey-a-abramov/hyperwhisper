package com.hyperwhisper.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.hyperwhisper.security.FakeSecretCipher
import com.hyperwhisper.security.SecretSlot
import com.hyperwhisper.security.SecretsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ApiSettingsRepositoryMigrationTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { File(tmp.root, "$name.preferences_pb") },
        )

    @Test fun `migrates plaintext api_keys_map and llm_config apiKey to encrypted store`() = runTest {
        val gson = Gson()
        val apiStore = store("api")
        val secretsStore = store("secrets")
        val secrets = SecretsRepository(secretsStore, FakeSecretCipher())

        // Pre-seed: plaintext keys map and an llm_config containing an apiKey,
        // exactly as the legacy version of the app would have written them.
        apiStore.edit { prefs ->
            prefs[stringPreferencesKey("api_provider")] = "OPENAI"
            prefs[stringPreferencesKey("api_keys_map")] =
                gson.toJson(mapOf("OPENAI" to "sk-openai-PLAIN", "DEEPGRAM" to "dg-PLAIN"))
            prefs[stringPreferencesKey("llm_config")] =
                gson.toJson(LlmConfig(apiKey = "llm-PLAIN", modelId = "gpt-4o-mini"))
        }

        val repo = ApiSettingsRepository(
            dataStore = apiStore,
            gson = gson,
            secretsRepository = secrets,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
        )
        // Wait until migration is fully done. The sentinel is set as the very
        // last step, so its presence implies plaintext has been stripped.
        secrets.awaitInitialLoad()
        apiStore.data.first { it[booleanPreferencesKey("secrets_migrated_v1")] == true }
        repo.apiSettings.first { it.apiKeys.isNotEmpty() }

        // 1. Plaintext is gone from the api settings DataStore.
        val rawApi = apiStore.data.first()
        assertNull(
            "Plaintext keys map must be removed",
            rawApi[stringPreferencesKey("api_keys_map")],
        )
        assertNull(
            "Legacy single api_key must be removed",
            rawApi[stringPreferencesKey("api_key")],
        )
        assertEquals(
            "Migration sentinel must be set",
            true,
            rawApi[booleanPreferencesKey("secrets_migrated_v1")],
        )

        // 2. llm_config JSON has been rewritten with apiKey="" — the model and
        //    other fields survive.
        val rewrittenLlmJson = rawApi[stringPreferencesKey("llm_config")]
        val rewrittenLlm = gson.fromJson(rewrittenLlmJson, LlmConfig::class.java)
        assertEquals("", rewrittenLlm.apiKey)
        assertEquals("gpt-4o-mini", rewrittenLlm.modelId)

        // 3. Secrets store now holds the migrated values, retrievable in plaintext.
        assertEquals("sk-openai-PLAIN", secrets.getOrEmpty(SecretSlot.Provider("OPENAI")))
        assertEquals("dg-PLAIN", secrets.getOrEmpty(SecretSlot.Provider("DEEPGRAM")))
        assertEquals("llm-PLAIN", secrets.getOrEmpty(SecretSlot.Llm))

        // 4. The secrets store on disk is encrypted, not plaintext.
        val rawSecrets = secretsStore.data.first()
        val openaiBlob = rawSecrets[stringPreferencesKey(SecretSlot.Provider("OPENAI").storageKey)]
        assertNotEquals(
            "Encrypted blob must not equal plaintext",
            "sk-openai-PLAIN",
            openaiBlob,
        )
        assertTrue(
            "Encrypted blob must look like cipher output",
            openaiBlob?.startsWith(FakeSecretCipher.PREFIX) == true,
        )
    }

    @Test fun `migration is idempotent — re-init does not duplicate or wipe`() = runTest {
        val gson = Gson()
        val apiStore = store("api2")
        val secretsStore = store("secrets2")
        val secrets = SecretsRepository(secretsStore, FakeSecretCipher())

        apiStore.edit { prefs ->
            prefs[stringPreferencesKey("api_keys_map")] = gson.toJson(mapOf("OPENAI" to "sk-1"))
        }

        val first = ApiSettingsRepository(
            apiStore, gson, secrets,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
        )
        secrets.awaitInitialLoad()
        first.apiSettings.first { it.apiKeys.isNotEmpty() }

        // Re-construct on the SAME stores (simulates next app boot post-migration).
        val second = ApiSettingsRepository(
            apiStore, gson, secrets,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
        )
        val emission = second.apiSettings.first()

        assertEquals(ApiProvider.OPENAI, emission.provider)
        assertEquals("sk-1", emission.apiKeys[ApiProvider.OPENAI])
        assertFalse(
            "Plaintext map must remain absent on re-init",
            apiStore.data.first().asMap().keys.any { it.name == "api_keys_map" },
        )
    }

    @Test fun `legacy single api_key is migrated to current provider slot`() = runTest {
        val gson = Gson()
        val apiStore = store("api3")
        val secretsStore = store("secrets3")
        val secrets = SecretsRepository(secretsStore, FakeSecretCipher())

        // Very old format: single `api_key` and `api_provider`, no `api_keys_map`.
        apiStore.edit { prefs ->
            prefs[stringPreferencesKey("api_provider")] = "GROQ"
            prefs[stringPreferencesKey("api_key")] = "groq-LEGACY-PLAIN"
        }

        ApiSettingsRepository(
            apiStore, gson, secrets,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
        )
        secrets.awaitInitialLoad()
        apiStore.data.first { it[booleanPreferencesKey("secrets_migrated_v1")] == true }

        val raw = apiStore.data.first()
        assertNull(raw[stringPreferencesKey("api_key")])
        assertEquals("groq-LEGACY-PLAIN", secrets.getOrEmpty(SecretSlot.Provider("GROQ")))
    }
}
