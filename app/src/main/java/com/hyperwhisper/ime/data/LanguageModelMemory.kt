package com.hyperwhisper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.languageModelMemoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hyperwhisper_language_model_memory"
)

/**
 * The transcription model the user last chose while a given input language was
 * active. Captures the full routing decision — cloud (provider + modelId) and
 * local Whisper (path + flag) — because the motivating case is Whisper, where
 * English transcribes best with one model and Russian needs another.
 */
data class LanguageModelChoice(
    val provider: ApiProvider,
    val modelId: String,
    val useLocalWhisper: Boolean,
    val whisperModelPath: String,
)

/**
 * Per-language transcription-model memory.
 *
 * Each input-language code gets its own remembered [LanguageModelChoice],
 * keyed under "lang_<code>" ("auto" for the empty/auto-detect language). When
 * the user switches language the keyboard recalls the stored model and applies
 * it; when they change the model the current language remembers it.
 *
 * Mirrors [PerAppLayoutMemory]: individual keys (atomic single-key writes) and
 * infallible (recall → null, remember → no-op on error) so a memory miss can
 * never block transcription.
 */
@Singleton
class LanguageModelMemory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
) {
    private val dataStore = context.languageModelMemoryDataStore

    /** Remember [choice] as the model for [languageCode]. */
    suspend fun remember(languageCode: String, choice: LanguageModelChoice) {
        try {
            dataStore.edit { it[key(languageCode)] = gson.toJson(choice) }
        } catch (_: Throwable) {
        }
    }

    /** The model last used for [languageCode], or null if none is remembered. */
    suspend fun recall(languageCode: String): LanguageModelChoice? = try {
        dataStore.data.first()[key(languageCode)]
            ?.let { gson.fromJson(it, LanguageModelChoice::class.java) }
    } catch (_: Throwable) {
        null
    }

    private fun key(languageCode: String) =
        stringPreferencesKey("lang_${languageCode.ifEmpty { "auto" }}")
}
