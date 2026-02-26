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

private val Context.providerModelTrackingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hyperwhisper_provider_model_tracking"
)

data class ProviderModelSelection(
    val provider: ApiProvider,
    val modelId: String
)

@Singleton
class ProviderModelTrackingRepository @Inject constructor(
    private val context: Context,
    private val gson: Gson
) {
    private val dataStore = context.providerModelTrackingDataStore

    companion object {
        private val RECENT_PROVIDER_MODELS_KEY = stringPreferencesKey("recent_provider_models")
        private const val MAX_RECENT_PROVIDER_MODELS = 8
    }

    private data class ProviderModelSelectionDto(
        val provider: String,
        val modelId: String
    )

    val recentlyUsedProviderModels: Flow<List<ProviderModelSelection>> = dataStore.data.map { preferences ->
        val json = preferences[RECENT_PROVIDER_MODELS_KEY]
        if (json.isNullOrEmpty()) {
            emptyList()
        } else {
            try {
                val type = object : TypeToken<List<ProviderModelSelectionDto>>() {}.type
                val dtoList = gson.fromJson<List<ProviderModelSelectionDto>>(json, type) ?: emptyList()
                dtoList.mapNotNull { dto ->
                    val provider = runCatching { ApiProvider.valueOf(dto.provider) }.getOrNull()
                    if (provider != null && dto.modelId.isNotBlank()) {
                        ProviderModelSelection(provider = provider, modelId = dto.modelId)
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun trackProviderModelUsage(provider: ApiProvider, modelId: String) {
        if (modelId.isBlank()) return

        dataStore.edit { preferences ->
            val currentJson = preferences[RECENT_PROVIDER_MODELS_KEY]
            val currentList = if (currentJson.isNullOrEmpty()) {
                emptyList()
            } else {
                try {
                    val type = object : TypeToken<List<ProviderModelSelectionDto>>() {}.type
                    gson.fromJson<List<ProviderModelSelectionDto>>(currentJson, type) ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val updatedList = (
                listOf(ProviderModelSelectionDto(provider = provider.name, modelId = modelId)) +
                    currentList.filterNot { it.provider == provider.name && it.modelId == modelId }
                ).take(MAX_RECENT_PROVIDER_MODELS)

            preferences[RECENT_PROVIDER_MODELS_KEY] = gson.toJson(updatedList)
        }
    }
}
