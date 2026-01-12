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
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "hyperwhisper_history")

/**
 * Repository for managing transcription history
 * Handles history items with optional audio files for reprocessing
 */
@Singleton
class HistoryRepository @Inject constructor(
    private val context: Context,
    private val gson: Gson,
    private val appearanceRepository: AppearanceRepository
) {
    private val dataStore = context.historyDataStore

    companion object {
        private val TRANSCRIPTION_HISTORY_KEY = stringPreferencesKey("transcription_history")
    }

    /**
     * Transcription History Flow
     * Emits list of historical transcription items with audio file references
     */
    val transcriptionHistory: Flow<List<TranscriptionHistoryItem>> = dataStore.data.map { preferences ->
        val historyJson = preferences[TRANSCRIPTION_HISTORY_KEY]
        if (historyJson.isNullOrEmpty()) {
            emptyList()
        } else {
            try {
                val type = object : TypeToken<List<TranscriptionHistoryItem>>() {}.type
                gson.fromJson(historyJson, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Add new item to history
     * Respects max history size and deletes audio files for trimmed items
     * Allows empty text if there's an audio file (for failed transcriptions that can be retried)
     */
    suspend fun addToHistory(text: String, audioFilePath: String? = null) {
        // Allow empty text if there's an audio file (for failed transcriptions that can be retried)
        if (text.isBlank() && audioFilePath == null) return

        dataStore.edit { preferences ->
            // Get current appearance settings to determine max history items
            val appearanceSettings = appearanceRepository.appearanceSettings.first()
            val maxHistoryItems = appearanceSettings.maxHistoryItems
            val unlimitedHistory = appearanceSettings.unlimitedHistory

            val currentHistoryJson = preferences[TRANSCRIPTION_HISTORY_KEY]
            val currentHistory = if (currentHistoryJson.isNullOrEmpty()) {
                emptyList()
            } else {
                try {
                    val type = object : TypeToken<List<TranscriptionHistoryItem>>() {}.type
                    gson.fromJson<List<TranscriptionHistoryItem>>(currentHistoryJson, type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }

            // Add new item at the beginning with audio file path
            val newItem = TranscriptionHistoryItem(text = text, audioFilePath = audioFilePath)
            val updatedHistory = listOf(newItem) + currentHistory

            // Trim history if not unlimited
            val trimmedHistory = if (unlimitedHistory) {
                updatedHistory
            } else {
                updatedHistory.take(maxHistoryItems)
            }

            // Delete audio files for items that are being removed
            val removedItems = if (unlimitedHistory) {
                emptyList()
            } else {
                updatedHistory.drop(maxHistoryItems)
            }
            removedItems.forEach { item ->
                item.audioFilePath?.let { path ->
                    try {
                        File(path).delete()
                    } catch (e: Exception) {
                        // Ignore deletion errors
                    }
                }
            }

            preferences[TRANSCRIPTION_HISTORY_KEY] = gson.toJson(trimmedHistory)
        }
    }

    /**
     * Update an existing history item with new transcription text
     * Used when reprocessing audio with different settings
     */
    suspend fun updateHistoryItem(itemId: String, newText: String) {
        dataStore.edit { preferences ->
            val currentHistoryJson = preferences[TRANSCRIPTION_HISTORY_KEY]
            if (!currentHistoryJson.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<List<TranscriptionHistoryItem>>() {}.type
                    val currentHistory = gson.fromJson<List<TranscriptionHistoryItem>>(currentHistoryJson, type) ?: emptyList()

                    // Find and update the item
                    val updatedHistory = currentHistory.map { item ->
                        if (item.id == itemId) {
                            item.copy(text = newText, timestamp = System.currentTimeMillis())
                        } else {
                            item
                        }
                    }

                    preferences[TRANSCRIPTION_HISTORY_KEY] = gson.toJson(updatedHistory)
                } catch (e: Exception) {
                    // Ignore parsing errors
                }
            }
        }
    }

    /**
     * Clear all history and delete all associated audio files
     */
    suspend fun clearHistory() {
        dataStore.edit { preferences ->
            // Get current history to delete audio files
            val currentHistoryJson = preferences[TRANSCRIPTION_HISTORY_KEY]
            if (!currentHistoryJson.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<List<TranscriptionHistoryItem>>() {}.type
                    val currentHistory = gson.fromJson<List<TranscriptionHistoryItem>>(currentHistoryJson, type)

                    // Delete all audio files
                    currentHistory?.forEach { item ->
                        item.audioFilePath?.let { path ->
                            try {
                                File(path).delete()
                            } catch (e: Exception) {
                                // Ignore deletion errors
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore parsing errors
                }
            }

            preferences.remove(TRANSCRIPTION_HISTORY_KEY)
        }
    }

    /**
     * Check how many items will be deleted if history size is reduced
     * Returns number of items that will be deleted (0 if none)
     */
    suspend fun checkHistorySizeReduction(newMaxSize: Int, newUnlimited: Boolean): Int {
        if (newUnlimited) return 0 // Unlimited means nothing deleted

        val currentHistory = transcriptionHistory.first()
        val itemsToDelete = (currentHistory.size - newMaxSize).coerceAtLeast(0)
        return itemsToDelete
    }

    /**
     * Trim history to new size, deleting excess items and their audio files
     */
    suspend fun trimHistoryToSize(maxSize: Int) {
        dataStore.edit { preferences ->
            val currentHistoryJson = preferences[TRANSCRIPTION_HISTORY_KEY]
            if (!currentHistoryJson.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<List<TranscriptionHistoryItem>>() {}.type
                    val currentHistory = gson.fromJson<List<TranscriptionHistoryItem>>(currentHistoryJson, type) ?: emptyList()

                    // Keep only the most recent maxSize items
                    val trimmedHistory = currentHistory.take(maxSize)

                    // Delete audio files for removed items
                    val removedItems = currentHistory.drop(maxSize)
                    removedItems.forEach { item ->
                        item.audioFilePath?.let { path ->
                            try {
                                File(path).delete()
                            } catch (e: Exception) {
                                // Ignore deletion errors
                            }
                        }
                    }

                    preferences[TRANSCRIPTION_HISTORY_KEY] = gson.toJson(trimmedHistory)
                } catch (e: Exception) {
                    // Ignore parsing errors
                }
            }
        }
    }
}
