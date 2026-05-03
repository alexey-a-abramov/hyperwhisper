package com.hyperwhisper.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A logical secret slot. New slots are added by extending this sealed type — each
 * slot maps to a distinct DataStore key, so secrets can be addressed by name
 * without colliding.
 */
sealed class SecretSlot(val storageKey: String) {
    /** Per-provider transcription/LLM API key. [providerName] is `ApiProvider.name`. */
    data class Provider(val providerName: String) : SecretSlot("provider:$providerName")

    /** Single LLM post-processing API key (not per-provider in the legacy model). */
    object Llm : SecretSlot("llm")
}

/**
 * Encrypted-at-rest store for sensitive material (API keys today; can be extended).
 *
 * Design notes:
 * - All values are encrypted via [SecretCipher] before they touch DataStore.
 * - A snapshot StateFlow is maintained for synchronous access by code paths that
 *   can't suspend (e.g. interceptors, ViewModel-derived states). It's primed
 *   from the first DataStore emission within milliseconds of construction.
 * - The repository is infallible from the caller's perspective: cipher failures,
 *   DataStore errors, etc. are swallowed and surface as "no secret stored".
 */
class SecretsRepository(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private val _snapshot = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Decrypted snapshot keyed by [SecretSlot.storageKey]. Empty until primed. */
    val snapshot: StateFlow<Map<String, String>> = _snapshot.asStateFlow()

    /** Reactive flow of decrypted secrets. */
    val secrets: Flow<Map<String, String>> = dataStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            val raw = value as? String ?: return@mapNotNull null
            val plain = cipher.decrypt(raw) ?: return@mapNotNull null
            key.name to plain
        }.toMap()
    }

    init {
        scope.launch {
            secrets.collect { _snapshot.value = it }
        }
    }

    /** Synchronous accessor; returns "" if the slot is unset or undecryptable. */
    fun getOrEmpty(slot: SecretSlot): String = _snapshot.value[slot.storageKey].orEmpty()

    /**
     * Stores [plaintext] under [slot]. An empty plaintext deletes the slot
     * entirely (so a wipe is symmetric with a write).
     */
    suspend fun put(slot: SecretSlot, plaintext: String) {
        dataStore.edit { prefs ->
            val k = stringPreferencesKey(slot.storageKey)
            if (plaintext.isEmpty()) {
                prefs.remove(k)
            } else {
                prefs[k] = cipher.encrypt(plaintext)
            }
        }
    }

    /** Bulk write — used by migration and import flows. */
    suspend fun putAll(entries: Map<SecretSlot, String>) {
        dataStore.edit { prefs ->
            entries.forEach { (slot, plaintext) ->
                val k = stringPreferencesKey(slot.storageKey)
                if (plaintext.isEmpty()) {
                    prefs.remove(k)
                } else {
                    prefs[k] = cipher.encrypt(plaintext)
                }
            }
        }
    }

    /** Suspends until the first DataStore emission has populated [snapshot]. */
    suspend fun awaitInitialLoad() {
        secrets.first()
    }

    /** Removes every stored secret. */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
