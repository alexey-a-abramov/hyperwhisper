package com.hyperwhisper.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Light-weight model summary fetched from openrouter.ai/api/v1/models. */
data class OpenRouterModelInfo(
    val id: String,
    val displayName: String,
    val isFree: Boolean,
    /** True when the model accepts audio input. Used by the transcription
     *  Cloud panel's "Audio-capable only" pre-filter. */
    val supportsAudio: Boolean,
    /** True when the model accepts text input. Used by the post-processing
     *  panel's "Text-capable only" pre-filter — defaults to true since most
     *  chat models accept text; rare audio-only models will filter out. */
    val acceptsText: Boolean,
    val contextLength: Long
)

/**
 * Fetches and caches OpenRouter's `/api/v1/models` catalog so the Settings UI
 * can render its "Discovery" panel without hitting the network on every
 * recomposition. Extracted from
 * [com.hyperwhisper.ui.settings.SettingsViewModel] to keep that VM focused.
 *
 * Note: as a `@Singleton`, the cached catalog and last error survive VM
 * recreation (e.g. screen rotation). [refreshOpenRouterModels] deliberately
 * keeps any prior catalog visible while a refresh is in flight.
 */
@Singleton
class OpenRouterDiscoveryService @Inject constructor(
    private val gson: Gson
) {
    companion object {
        private const val TAG = "OpenRouterDiscovery"
    }

    private val _openRouterModels = MutableStateFlow<List<OpenRouterModelInfo>>(emptyList())
    val openRouterModels: StateFlow<List<OpenRouterModelInfo>> = _openRouterModels.asStateFlow()

    private val _openRouterRefreshing = MutableStateFlow(false)
    val openRouterRefreshing: StateFlow<Boolean> = _openRouterRefreshing.asStateFlow()

    private val _openRouterError = MutableStateFlow<String?>(null)
    val openRouterError: StateFlow<String?> = _openRouterError.asStateFlow()

    /**
     * Fetch OpenRouter's catalog (`/api/v1/models`). Updates [openRouterModels],
     * [openRouterRefreshing], [openRouterError]. Each entry carries flags so
     * the UI can apply free/transcription filters client-side.
     */
    suspend fun refreshOpenRouterModels() {
        _openRouterRefreshing.value = true
        _openRouterError.value = null
        try {
            val parsed = withContext(Dispatchers.IO) { fetchOpenRouterCatalog() }
            _openRouterModels.value = parsed
            Log.d(TAG, "OpenRouter catalog: ${parsed.size} models")
        } catch (t: Throwable) {
            _openRouterError.value = t.message ?: t.javaClass.simpleName
            Log.w(TAG, "OpenRouter refresh failed", t)
        } finally {
            _openRouterRefreshing.value = false
        }
    }

    private fun fetchOpenRouterCatalog(): List<OpenRouterModelInfo> {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder()
            .url("https://openrouter.ai/api/v1/models")
            .get()
            .build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val root = gson.fromJson(body, JsonObject::class.java) ?: return emptyList()
            val data = root["data"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
            return data.mapNotNull { el ->
                runCatching {
                    val obj = el.asJsonObject
                    val id = obj["id"]?.asString ?: return@runCatching null
                    val name = obj["name"]?.takeIf { !it.isJsonNull }?.asString ?: id
                    val ctx = obj["context_length"]?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                    val pricing = obj["pricing"]?.takeIf { it.isJsonObject }?.asJsonObject
                    val free = isFreeModel(id, pricing)
                    val arch = obj["architecture"]?.takeIf { it.isJsonObject }?.asJsonObject
                    val inputModalities = arch?.get("input_modalities")
                        ?.takeIf { it.isJsonArray }
                        ?.asJsonArray
                        ?.mapNotNull { runCatching { it.asString }.getOrNull() }
                        ?: emptyList()
                    val supportsAudio = "audio" in inputModalities ||
                        id.contains("whisper", ignoreCase = true) ||
                        id.contains("transcribe", ignoreCase = true) ||
                        id.contains("voxtral", ignoreCase = true)
                    // Default to true when the catalog doesn't list modalities
                    // — empty/missing input_modalities is overwhelmingly chat
                    // models on OpenRouter.
                    val acceptsText = inputModalities.isEmpty() ||
                        "text" in inputModalities
                    OpenRouterModelInfo(
                        id = id,
                        displayName = name,
                        isFree = free,
                        supportsAudio = supportsAudio,
                        acceptsText = acceptsText,
                        contextLength = ctx
                    )
                }.getOrNull()
            }
        }
    }

    private fun isFreeModel(id: String, pricing: JsonObject?): Boolean {
        if (id.endsWith(":free", ignoreCase = true)) return true
        if (pricing == null) return false
        val keys = listOf("prompt", "completion")
        return keys.all { k ->
            val v = pricing[k]?.takeIf { !it.isJsonNull }?.asString ?: return@all false
            (v.toDoubleOrNull() ?: -1.0) == 0.0
        }
    }
}
