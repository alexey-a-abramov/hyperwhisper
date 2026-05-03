package com.hyperwhisper.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SecretsRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { File(tmp.root, "$name.preferences_pb") },
        )

    @Test fun `put then getOrEmpty returns plaintext`() = runTest {
        val repo = SecretsRepository(store("a"), FakeSecretCipher())
        repo.put(SecretSlot.Provider("OPENAI"), "sk-live-12345")
        repo.awaitInitialLoad()

        assertEquals("sk-live-12345", repo.getOrEmpty(SecretSlot.Provider("OPENAI")))
        assertEquals("", repo.getOrEmpty(SecretSlot.Provider("DEEPGRAM")))
    }

    @Test fun `put with empty value deletes the slot`() = runTest {
        val repo = SecretsRepository(store("b"), FakeSecretCipher())
        repo.put(SecretSlot.Provider("OPENAI"), "sk-live-12345")
        repo.awaitInitialLoad()
        assertEquals("sk-live-12345", repo.getOrEmpty(SecretSlot.Provider("OPENAI")))

        repo.put(SecretSlot.Provider("OPENAI"), "")
        // Force re-read of latest snapshot.
        val secrets = repo.secrets.first()
        assertFalse(
            "Empty plaintext should remove the slot entirely",
            secrets.containsKey(SecretSlot.Provider("OPENAI").storageKey),
        )
    }

    @Test fun `putAll persists multiple slots`() = runTest {
        val repo = SecretsRepository(store("c"), FakeSecretCipher())
        repo.putAll(
            mapOf(
                SecretSlot.Provider("OPENAI") to "sk-openai",
                SecretSlot.Provider("DEEPGRAM") to "dg-key",
                SecretSlot.Llm to "llm-key",
            ),
        )

        // Read via the Flow rather than the in-memory snapshot — the snapshot
        // is populated by a separate collector coroutine and may lag the write.
        val secrets = repo.secrets.first { it.size >= 3 }
        assertEquals("sk-openai", secrets[SecretSlot.Provider("OPENAI").storageKey])
        assertEquals("dg-key", secrets[SecretSlot.Provider("DEEPGRAM").storageKey])
        assertEquals("llm-key", secrets[SecretSlot.Llm.storageKey])
    }

    @Test fun `disk content is encrypted, never plaintext`() = runTest {
        val ds = store("d")
        val repo = SecretsRepository(ds, FakeSecretCipher())
        repo.put(SecretSlot.Provider("OPENAI"), "sk-live-PLAIN-MARKER")
        repo.awaitInitialLoad()

        // Read raw DataStore preferences (bypass the cipher) and check the stored
        // value is the wrapped form, not the plaintext.
        val k = stringPreferencesKey(SecretSlot.Provider("OPENAI").storageKey)
        val raw = ds.data.first()[k]
        assertNotEquals(
            "Stored value must not equal plaintext",
            "sk-live-PLAIN-MARKER",
            raw,
        )
        assertTrue(
            "Stored value must look like cipher output",
            raw?.startsWith(FakeSecretCipher.PREFIX) == true,
        )
    }

    @Test fun `corrupted ciphertext yields empty, not crash`() = runTest {
        val ds = store("e")
        // Manually plant a malformed ciphertext under a real slot key.
        ds.edit { prefs ->
            prefs[stringPreferencesKey(SecretSlot.Provider("OPENAI").storageKey)] = "not-a-valid-blob"
        }
        val repo = SecretsRepository(ds, FakeSecretCipher())
        repo.awaitInitialLoad()

        assertEquals("", repo.getOrEmpty(SecretSlot.Provider("OPENAI")))
    }

    @Test fun `clearAll empties the store`() = runTest {
        val repo = SecretsRepository(store("f"), FakeSecretCipher())
        repo.putAll(
            mapOf(
                SecretSlot.Provider("OPENAI") to "sk",
                SecretSlot.Llm to "llm",
            ),
        )
        repo.awaitInitialLoad()
        repo.clearAll()

        val secrets = repo.secrets.first()
        assertTrue("clearAll must wipe all entries", secrets.isEmpty())
    }
}

