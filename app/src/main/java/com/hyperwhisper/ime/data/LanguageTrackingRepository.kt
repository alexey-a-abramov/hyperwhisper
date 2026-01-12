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

private val Context.languageTrackingDataStore: DataStore<Preferences> by preferencesDataStore(name = "hyperwhisper_language_tracking")

/**
 * Repository for tracking recently used languages
 * Maintains a list of recently selected languages for quick access
 */
@Singleton
class LanguageTrackingRepository @Inject constructor(
    private val context: Context,
    private val gson: Gson
) {
    private val dataStore = context.languageTrackingDataStore

    companion object {
        private val RECENTLY_USED_LANGUAGES_KEY = stringPreferencesKey("recently_used_languages")
        private const val MAX_RECENT_LANGUAGES = 5
    }

    /**
     * Recently Used Languages Flow
     * Emits list of recently selected language codes (max 5)
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

    /**
     * Track language usage
     * Adds language to the front of the list, removing duplicates
     * Doesn't track empty (auto-detect) or "en" (English) as they're always at the top
     */
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
}
