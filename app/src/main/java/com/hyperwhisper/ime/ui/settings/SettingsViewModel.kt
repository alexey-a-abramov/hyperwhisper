package com.hyperwhisper.ui.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.hyperwhisper.R
import com.hyperwhisper.data.ApiCallLog
import com.hyperwhisper.data.ApiCallStatistics
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.AppearanceSettings
import com.hyperwhisper.data.LlmConfig
import com.hyperwhisper.data.LlmProvider
import com.hyperwhisper.data.SettingsRepository
import com.hyperwhisper.data.VoiceMode
import com.hyperwhisper.data.WhisperDownloadState
import com.hyperwhisper.data.WhisperModelCatalog
import com.hyperwhisper.data.WhisperModelDownloader
import com.hyperwhisper.data.ChatCompletionRequest
import com.hyperwhisper.data.ChatMessage
import com.hyperwhisper.data.ContentPart
import com.hyperwhisper.network.ChatCompletionApiService
import com.hyperwhisper.network.TranscriptionApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed class ConnectionTestState {
    object Idle : ConnectionTestState()
    object Testing : ConnectionTestState()
    data class Success(val message: String) : ConnectionTestState()
    data class Error(val message: String) : ConnectionTestState()
}

enum class TestLogLevel { INFO, RUNNING, OK, FAIL }

data class TestLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: TestLogLevel,
    val message: String,
    val detail: String? = null
)

data class OpenRouterModelInfo(
    val id: String,
    val displayName: String,
    val isFree: Boolean,
    val supportsAudio: Boolean,
    val contextLength: Long
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val transcriptionApiService: TranscriptionApiService,
    private val chatCompletionApiService: ChatCompletionApiService,
    private val gson: Gson,
    private val whisperDownloader: WhisperModelDownloader,
    private val gemmaDownloader: com.hyperwhisper.data.GemmaModelDownloader,
    private val gemma: com.hyperwhisper.ime.llm.GemmaInferenceEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    val apiSettings: StateFlow<ApiSettings> = settingsRepository.apiSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ApiSettings())

    val voiceModes: StateFlow<List<VoiceMode>> = settingsRepository.voiceModes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val appearanceSettings: StateFlow<AppearanceSettings> = settingsRepository.appearanceSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceSettings())

    val apiCallLogs: StateFlow<List<ApiCallLog>> = settingsRepository.apiCallLogs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _apiCallStatistics = MutableStateFlow(ApiCallStatistics(0, 0, 0, emptyMap(), 0))
    val apiCallStatistics: StateFlow<ApiCallStatistics> = _apiCallStatistics.asStateFlow()

    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    private val _postProcessingTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val postProcessingTestState: StateFlow<ConnectionTestState> = _postProcessingTestState.asStateFlow()

    private val _transcriptionTestLog = MutableStateFlow<List<TestLogEntry>>(emptyList())
    val transcriptionTestLog: StateFlow<List<TestLogEntry>> = _transcriptionTestLog.asStateFlow()

    private val _postProcessingTestLog = MutableStateFlow<List<TestLogEntry>>(emptyList())
    val postProcessingTestLog: StateFlow<List<TestLogEntry>> = _postProcessingTestLog.asStateFlow()

    private val _discoveredModels = MutableStateFlow<List<com.hyperwhisper.data.LocalModelInfo>>(emptyList())
    val discoveredModels: StateFlow<List<com.hyperwhisper.data.LocalModelInfo>> = _discoveredModels.asStateFlow()

    val whisperDownloadStates: StateFlow<Map<String, WhisperDownloadState>> = whisperDownloader.states
    val gemmaDownloadStates: StateFlow<Map<String, com.hyperwhisper.data.GemmaDownloadState>> =
        gemmaDownloader.states

    fun startGemmaDownload(modelId: String) {
        val entry = com.hyperwhisper.data.GemmaModelCatalog.byId(modelId) ?: return
        gemmaDownloader.start(entry)
    }
    fun cancelGemmaDownload(modelId: String) = gemmaDownloader.cancel(modelId)
    fun deleteDownloadedGemmaModel(modelId: String) {
        val entry = com.hyperwhisper.data.GemmaModelCatalog.byId(modelId) ?: return
        gemmaDownloader.delete(entry)
    }
    fun setActiveGemmaModel(path: String) {
        viewModelScope.launch {
            val s = apiSettings.value.localModelSettings
            settingsRepository.updateLocalModelSettings(
                s.copy(gemmaModelPath = path, useLocalGemma = true)
            )
        }
    }

    private val _openRouterModels = MutableStateFlow<List<OpenRouterModelInfo>>(emptyList())
    val openRouterModels: StateFlow<List<OpenRouterModelInfo>> = _openRouterModels.asStateFlow()

    private val _openRouterRefreshing = MutableStateFlow(false)
    val openRouterRefreshing: StateFlow<Boolean> = _openRouterRefreshing.asStateFlow()

    private val _openRouterError = MutableStateFlow<String?>(null)
    val openRouterError: StateFlow<String?> = _openRouterError.asStateFlow()

    init {
        // Update statistics whenever logs change
        viewModelScope.launch {
            apiCallLogs.collect {
                _apiCallStatistics.value = settingsRepository.getApiCallStatistics()
            }
        }
        
        // Initial model discovery
        viewModelScope.launch {
            apiSettings.collect { settings ->
                if (settings.localModelSettings.autoDiscover && _discoveredModels.value.isEmpty()) {
                    discoverModels()
                }
            }
        }
    }

    fun discoverModels() {
        viewModelScope.launch {
            try {
                _discoveredModels.value = settingsRepository.localModelRepository.discoverModels()
                Log.d(TAG, "Discovered ${_discoveredModels.value.size} local models")
            } catch (e: Exception) {
                Log.e(TAG, "Error discovering models", e)
            }
        }
    }

    fun verifyModelIntegrity(path: String) {
        viewModelScope.launch {
            try {
                val hash = settingsRepository.localModelRepository.verifyIntegrity(path)
                // Update the model info in our list with the hash
                _discoveredModels.value = _discoveredModels.value.map {
                    if (it.path == path) it.copy(hash = hash, isVerified = hash.isNotEmpty()) else it
                }
                Log.d(TAG, "Verified model integrity for: $path, hash: $hash")
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying model integrity", e)
            }
        }
    }

    fun updateLocalModelSettings(settings: com.hyperwhisper.data.LocalModelSettings) {
        viewModelScope.launch {
            try {
                settingsRepository.updateLocalModelSettings(settings)
                Log.d(TAG, "Local model settings updated: $settings")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating local model settings", e)
            }
        }
    }

    fun saveApiSettings(
        provider: ApiProvider,
        baseUrl: String,
        apiKey: String,
        requiresAuth: Boolean,
        modelId: String,
        inputLanguage: String = "",
        outputLanguage: String = ""
    ) {
        viewModelScope.launch {
            saveApiSettingsInternal(provider, baseUrl, apiKey, requiresAuth, modelId, inputLanguage, outputLanguage)
        }
    }

    /**
     * Suspend version that waits for the save to complete before returning.
     * Use this when you need to ensure settings are saved before proceeding (e.g., before closing activity).
     */
    suspend fun saveApiSettingsAndWait(
        provider: ApiProvider,
        baseUrl: String,
        apiKey: String,
        requiresAuth: Boolean,
        modelId: String,
        inputLanguage: String = "",
        outputLanguage: String = ""
    ) {
        saveApiSettingsInternal(provider, baseUrl, apiKey, requiresAuth, modelId, inputLanguage, outputLanguage)
    }

    private suspend fun saveApiSettingsInternal(
        provider: ApiProvider,
        baseUrl: String,
        apiKey: String,
        requiresAuth: Boolean,
        modelId: String,
        inputLanguage: String,
        outputLanguage: String
    ) {
        try {
            // Get current settings to preserve other provider data and LLM config
            val currentSettings = apiSettings.value
            val updatedApiKeys = currentSettings.apiKeys.toMutableMap()
            updatedApiKeys[provider] = apiKey.trim()

            val updatedConfigs = currentSettings.providerConfigs.toMutableMap()
            updatedConfigs[provider] = com.hyperwhisper.data.ProviderConfig(
                customBaseUrl = baseUrl.trim(),
                requiresAuth = requiresAuth
            )

            val settings = ApiSettings(
                provider = provider,
                baseUrl = baseUrl.trim(),
                apiKeys = updatedApiKeys,
                providerConfigs = updatedConfigs,
                modelId = modelId.trim(),
                inputLanguage = inputLanguage.trim(),
                outputLanguage = outputLanguage.trim(),
                llmConfig = currentSettings.llmConfig // Preserve LLM config
            )
            settingsRepository.saveApiSettings(settings)
            Log.d(TAG, "API settings saved: $provider, $baseUrl, requiresAuth: $requiresAuth, model: $modelId")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving API settings", e)
        }
    }

    fun updateProviderApiKey(provider: ApiProvider, apiKey: String) {
        viewModelScope.launch {
            try {
                settingsRepository.updateProviderApiKey(provider, apiKey.trim())
                Log.d(TAG, "API key updated for provider: ${provider.displayName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating provider API key", e)
            }
        }
    }

    fun updateProviderConfig(provider: ApiProvider, customBaseUrl: String, requiresAuth: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.updateProviderConfig(provider, customBaseUrl.trim(), requiresAuth)
                Log.d(TAG, "Provider config updated for: ${provider.displayName}, requiresAuth: $requiresAuth")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating provider config", e)
            }
        }
    }

    fun updateLlmConfig(llmConfig: com.hyperwhisper.data.LlmConfig) {
        viewModelScope.launch {
            try {
                settingsRepository.updateLlmConfig(llmConfig)
                Log.d(TAG, "LLM config updated: ${llmConfig.provider.displayName}, model: ${llmConfig.modelId}")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating LLM config", e)
            }
        }
    }

    fun resetToDefaults(provider: ApiProvider) {
        viewModelScope.launch {
            try {
                settingsRepository.resetApiSettingsToDefaults(provider)
                Log.d(TAG, "Reset settings to defaults for provider: ${provider.displayName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting to defaults", e)
            }
        }
    }

    fun saveAppearanceSettings(settings: AppearanceSettings) {
        viewModelScope.launch {
            try {
                // Check if history size is being reduced
                val currentSettings = appearanceSettings.value
                val needsTrimming = !settings.unlimitedHistory &&
                    (settings.maxHistoryItems < currentSettings.maxHistoryItems ||
                     (currentSettings.unlimitedHistory && !settings.unlimitedHistory))

                // Save settings first
                settingsRepository.saveAppearanceSettings(settings)
                Log.d(TAG, "Appearance settings saved: $settings")

                // Trim history if needed
                if (needsTrimming) {
                    settingsRepository.trimHistoryToSize(settings.maxHistoryItems)
                    Log.d(TAG, "History trimmed to ${settings.maxHistoryItems} items")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving appearance settings", e)
            }
        }
    }

    fun addVoiceMode(name: String, systemPrompt: String) {
        viewModelScope.launch {
            try {
                val mode = VoiceMode(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    systemPrompt = systemPrompt.trim(),
                    isBuiltIn = false
                )
                settingsRepository.addVoiceMode(mode)
                Log.d(TAG, "Voice mode added: ${mode.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding voice mode", e)
            }
        }
    }

    fun deleteVoiceMode(modeId: String) {
        viewModelScope.launch {
            try {
                settingsRepository.deleteVoiceMode(modeId)
                Log.d(TAG, "Voice mode deleted: $modeId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting voice mode", e)
            }
        }
    }

    fun updateVoiceMode(mode: VoiceMode) {
        viewModelScope.launch {
            try {
                settingsRepository.updateVoiceMode(mode)
                Log.d(TAG, "Voice mode updated: ${mode.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating voice mode", e)
            }
        }
    }

    /**
     * Sends a real bundled speech sample (JFK clip, ~12s) to the configured
     * transcription endpoint and reports back the transcribed text. Emits
     * step-by-step entries to [transcriptionTestLog] so the UI can show
     * progress live.
     */
    fun testConnection(provider: ApiProvider, baseUrl: String, apiKey: String, modelId: String) {
        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState.Testing
            _transcriptionTestLog.value = emptyList()
            appendTranscriptionLog(TestLogLevel.RUNNING, "Starting transcription test")

            val local = apiSettings.value.localModelSettings
            if (local.useLocalWhisper) {
                runLocalWhisperTest(local)
                return@launch
            }

            appendTranscriptionLog(TestLogLevel.INFO, "Provider: ${provider.displayName}", "model=$modelId")
            appendTranscriptionLog(TestLogLevel.INFO, "Endpoint: $baseUrl")

            if (provider.usesChatAudioForTranscription()) {
                runChatAudioTranscriptionTest(provider, baseUrl, apiKey, modelId)
                return@launch
            }

            try {
                if (provider == ApiProvider.SELFHOSTED_WHISPER) {
                    appendTranscriptionLog(TestLogLevel.RUNNING, "Probing self-hosted whisper.cpp (GET)")
                    val started = System.nanoTime()
                    val request = Request.Builder()
                        .url(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                        .get()
                        .build()
                    OkHttpClient.Builder().build().newCall(request).execute().use { response ->
                        val ms = (System.nanoTime() - started) / 1_000_000
                        if (response.isSuccessful) {
                            appendTranscriptionLog(TestLogLevel.OK, "HTTP 200 in ${ms} ms")
                            _connectionTestState.value = ConnectionTestState.Success(
                                "Local whisper.cpp server is responding."
                            )
                            return@launch
                        }
                        throw IllegalStateException("HTTP ${response.code}")
                    }
                }

                appendTranscriptionLog(TestLogLevel.RUNNING, "Loading sample audio")
                val audioBytes = withContext(Dispatchers.IO) { loadSampleSpeechBytes() }
                val audioSeconds = wavDurationSeconds(audioBytes)
                appendTranscriptionLog(
                    TestLogLevel.INFO,
                    "Loaded sample_speech.wav",
                    buildString {
                        append("${audioBytes.size / 1024} KB")
                        if (audioSeconds != null) append(" · ${"%.2f".format(audioSeconds)} s audio")
                    }
                )

                appendTranscriptionLog(TestLogLevel.RUNNING, "POST audio/transcriptions")
                val audioPart = MultipartBody.Part.createFormData(
                    "file",
                    "sample.wav",
                    audioBytes.toRequestBody("audio/wav".toMediaTypeOrNull())
                )
                val modelPart = modelId.toRequestBody("text/plain".toMediaTypeOrNull())
                val started = System.nanoTime()
                val response = transcriptionApiService.transcribe(audioPart, modelPart)
                val elapsedMs = (System.nanoTime() - started) / 1_000_000
                appendTranscriptionLog(
                    TestLogLevel.INFO,
                    "Response HTTP ${response.code()}",
                    "${elapsedMs} ms"
                )

                if (!response.isSuccessful) {
                    val errBody = response.errorBody()?.string()?.take(400).orEmpty()
                    if (errBody.isNotBlank()) {
                        appendTranscriptionLog(TestLogLevel.FAIL, "Error body", errBody)
                    }
                    if (response.code() == 403 && errBody.contains("unsupported_country", ignoreCase = true)) {
                        appendTranscriptionLog(
                            TestLogLevel.INFO,
                            "Tip — provider geo-blocks your region",
                            "Try Groq Whisper (whisper-large-v3 / whisper-large-v3-turbo) or Hugging Face. OpenAI and providers proxying to it (incl. some OpenRouter routes) reject this region."
                        )
                    } else if (response.code() == 403 || response.code() == 404) {
                        appendTranscriptionLog(
                            TestLogLevel.INFO,
                            "Tip — model may not support audio/transcriptions",
                            "OpenRouter routes only whisper-style ids through /audio/transcriptions. Chat-only models (Nemotron, Llama-instruct, …) will 403/404 here."
                        )
                    }
                    throw IllegalStateException("HTTP ${response.code()}")
                }

                val text = response.body()?.text?.trim().orEmpty()
                if (text.isBlank()) {
                    appendTranscriptionLog(TestLogLevel.FAIL, "Empty response body")
                    throw IllegalStateException("Empty transcription returned")
                }
                val preview = "“${text.take(120)}${if (text.length > 120) "…" else ""}”"
                appendTranscriptionLog(TestLogLevel.OK, "Transcription returned", "${text.length} chars")
                appendTranscriptionLog(TestLogLevel.INFO, preview)

                val speedDetail = formatSpeedDetail(audioSeconds, elapsedMs)
                if (speedDetail != null) {
                    appendTranscriptionLog(TestLogLevel.OK, "Speed", speedDetail)
                }
                val successMsg = buildString {
                    append("Transcribed: $preview")
                    if (speedDetail != null) append("  ·  $speedDetail")
                }
                _connectionTestState.value = ConnectionTestState.Success(successMsg)
                Log.d(TAG, "Transcription test ok; len=${text.length}")
            } catch (e: Exception) {
                appendTranscriptionLog(
                    TestLogLevel.FAIL,
                    e.javaClass.simpleName,
                    e.message ?: "no message"
                )
                _connectionTestState.value = ConnectionTestState.Error(prettifyError(e))
                Log.e(TAG, "Transcription test failed", e)
            }
        }
    }

    fun resetConnectionTestState() {
        _connectionTestState.value = ConnectionTestState.Idle
        _transcriptionTestLog.value = emptyList()
    }

    /**
     * Send the bundled JFK sample to an OpenAI-compatible /chat/completions
     * endpoint as a multimodal user message (text + input_audio). This is the
     * path OpenRouter, Gemini, and Antigravity actually take in production via
     * [com.hyperwhisper.network.ChatCompletionStrategy].
     */
    private suspend fun runChatAudioTranscriptionTest(
        provider: ApiProvider,
        baseUrl: String,
        apiKey: String,
        modelId: String
    ) {
        try {
            appendTranscriptionLog(TestLogLevel.RUNNING, "Loading sample audio")
            val audioBytes = withContext(Dispatchers.IO) { loadSampleSpeechBytes() }
            val audioSeconds = wavDurationSeconds(audioBytes)
            appendTranscriptionLog(
                TestLogLevel.INFO,
                "Loaded sample_speech.wav",
                buildString {
                    append("${audioBytes.size / 1024} KB")
                    if (audioSeconds != null) append(" · ${"%.2f".format(audioSeconds)} s audio")
                }
            )

            val base64 = withContext(Dispatchers.Default) {
                android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)
            }
            appendTranscriptionLog(TestLogLevel.INFO, "Base64-encoded audio", "${base64.length} chars")

            val request = ChatCompletionRequest(
                model = modelId,
                messages = listOf(
                    ChatMessage(
                        role = "user",
                        content = listOf(
                            ContentPart.TextContent(text = "Transcribe this audio verbatim. Return only the transcribed text."),
                            ContentPart.AudioContent(
                                inputAudio = com.hyperwhisper.data.InputAudio(data = base64, format = "wav")
                            )
                        )
                    )
                ),
                modalities = listOf("text")
            )

            appendTranscriptionLog(
                TestLogLevel.RUNNING,
                "POST chat/completions",
                "input_audio block with text-only output"
            )
            val service = withContext(Dispatchers.IO) {
                createChatAudioTestClient(baseUrl, apiKey, provider)
            }
            val started = System.nanoTime()
            val response = service.chatCompletion(request)
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            appendTranscriptionLog(
                TestLogLevel.INFO,
                "Response HTTP ${response.code()}",
                "${elapsedMs} ms"
            )

            if (!response.isSuccessful) {
                val errBody = response.errorBody()?.string()?.take(400).orEmpty()
                if (errBody.isNotBlank()) {
                    appendTranscriptionLog(TestLogLevel.FAIL, "Error body", errBody)
                }
                if (response.code() == 404 && provider == ApiProvider.OPENROUTER) {
                    appendTranscriptionLog(
                        TestLogLevel.INFO,
                        "Tip — only voxtral models support audio on OpenRouter",
                        "Try mistralai/voxtral-small-24b-2507. Chat-only models will 404 with input_audio blocks."
                    )
                }
                throw IllegalStateException("HTTP ${response.code()}")
            }

            val text = response.body()?.choices?.firstOrNull()?.message?.content?.trim().orEmpty()
            if (text.isBlank()) {
                appendTranscriptionLog(TestLogLevel.FAIL, "Empty response body")
                throw IllegalStateException("Empty transcription returned")
            }
            val preview = "“${text.take(120)}${if (text.length > 120) "…" else ""}”"
            appendTranscriptionLog(TestLogLevel.OK, "Transcription returned", "${text.length} chars")
            appendTranscriptionLog(TestLogLevel.INFO, preview)

            val speedDetail = formatSpeedDetail(audioSeconds, elapsedMs)
            if (speedDetail != null) {
                appendTranscriptionLog(TestLogLevel.OK, "Speed", speedDetail)
            }
            val successMsg = buildString {
                append("Transcribed: $preview")
                if (speedDetail != null) append("  ·  $speedDetail")
            }
            _connectionTestState.value = ConnectionTestState.Success(successMsg)
            Log.d(TAG, "Chat-audio test ok; provider=${provider.name} len=${text.length}")
        } catch (e: Exception) {
            appendTranscriptionLog(
                TestLogLevel.FAIL,
                e.javaClass.simpleName,
                e.message ?: "no message"
            )
            _connectionTestState.value = ConnectionTestState.Error(prettifyError(e))
            Log.e(TAG, "Chat-audio test failed", e)
        }
    }

    private fun createChatAudioTestClient(
        baseUrl: String,
        apiKey: String,
        provider: ApiProvider
    ): ChatCompletionApiService {
        val auth = Interceptor { chain ->
            val rb = chain.request().newBuilder()
            if (apiKey.isNotBlank()) {
                rb.addHeader("Authorization", "Bearer $apiKey")
            }
            rb.addHeader("Content-Type", "application/json")
            // OpenRouter recommends these for routing/observability.
            if (provider == ApiProvider.OPENROUTER) {
                rb.addHeader("HTTP-Referer", "https://github.com/hyperwhisper")
                rb.addHeader("X-Title", "HyperWhisper")
            }
            chain.proceed(rb.build())
        }
        val ok = OkHttpClient.Builder()
            .addInterceptor(auth)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
        val finalUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(finalUrl)
            .client(ok)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ChatCompletionApiService::class.java)
    }

    /**
     * When on-device Whisper is the active source, the test verifies the model
     * file and bundled sample without dispatching to a cloud endpoint. JNI
     * inference is still TODO, so we stop short of running a real transcription
     * and report that fact honestly instead of falsely succeeding.
     */
    private suspend fun runLocalWhisperTest(local: com.hyperwhisper.data.LocalModelSettings) {
        appendTranscriptionLog(TestLogLevel.INFO, "Source: On-device Whisper")
        if (local.whisperModelPath.isBlank()) {
            appendTranscriptionLog(
                TestLogLevel.FAIL,
                "No Whisper model selected",
                "Pick one under Local or download via Tools."
            )
            _connectionTestState.value = ConnectionTestState.Error("No on-device Whisper model selected.")
            return
        }
        appendTranscriptionLog(TestLogLevel.INFO, "Model path", local.whisperModelPath)

        val file = java.io.File(local.whisperModelPath)
        if (!file.exists()) {
            appendTranscriptionLog(
                TestLogLevel.FAIL,
                "Model file missing",
                local.whisperModelPath
            )
            _connectionTestState.value =
                ConnectionTestState.Error("Configured model file not found on disk.")
            return
        }
        if (!file.canRead()) {
            appendTranscriptionLog(
                TestLogLevel.FAIL,
                "Model file not readable",
                "Check storage permission (MANAGE_EXTERNAL_STORAGE)."
            )
            _connectionTestState.value =
                ConnectionTestState.Error("Configured model file is not readable.")
            return
        }
        appendTranscriptionLog(
            TestLogLevel.OK,
            "Model file present",
            "${file.length() / 1024 / 1024} MB"
        )

        appendTranscriptionLog(TestLogLevel.RUNNING, "Loading bundled sample audio")
        val audio = try {
            withContext(Dispatchers.IO) { loadSampleSpeechBytes() }
        } catch (t: Throwable) {
            appendTranscriptionLog(TestLogLevel.FAIL, "Sample load failed", t.message ?: t.javaClass.simpleName)
            _connectionTestState.value = ConnectionTestState.Error("Could not load bundled audio sample.")
            return
        }
        val audioSeconds = wavDurationSeconds(audio)
        appendTranscriptionLog(
            TestLogLevel.OK,
            "Sample loaded",
            buildString {
                append("${audio.size / 1024} KB")
                if (audioSeconds != null) append(" · ${"%.2f".format(audioSeconds)} s audio")
            }
        )

        appendTranscriptionLog(TestLogLevel.RUNNING, "Loading whisper.cpp model into memory")
        val ctx = try {
            withContext(Dispatchers.IO) {
                com.hyperwhisper.ime.whisper.WhisperContext.createFromFile(local.whisperModelPath)
            }
        } catch (t: Throwable) {
            appendTranscriptionLog(
                TestLogLevel.FAIL,
                "whisper_init_from_file failed",
                t.message ?: t.javaClass.simpleName
            )
            _connectionTestState.value = ConnectionTestState.Error(
                "Could not initialize whisper.cpp on the model file. ${t.message ?: ""}"
            )
            return
        }
        appendTranscriptionLog(TestLogLevel.OK, "Model loaded into whisper.cpp")

        try {
            appendTranscriptionLog(TestLogLevel.RUNNING, "Decoding sample WAV → float32 PCM")
            val samples = withContext(Dispatchers.Default) { wavToFloatMono16k(audio) }
            appendTranscriptionLog(
                TestLogLevel.INFO,
                "Decoded sample",
                "${samples.size} samples"
            )

            appendTranscriptionLog(TestLogLevel.RUNNING, "Running whisper_full")
            val started = System.nanoTime()
            val text = ctx.transcribe(samples = samples, language = "en")
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            appendTranscriptionLog(
                TestLogLevel.OK,
                "Inference complete",
                "${elapsedMs} ms"
            )

            val trimmed = text.trim()
            if (trimmed.isBlank()) {
                appendTranscriptionLog(TestLogLevel.FAIL, "Empty transcription returned")
                _connectionTestState.value = ConnectionTestState.Error(
                    "whisper.cpp returned no segments. Model may be corrupt or audio sample failed to decode."
                )
                return
            }
            val preview = "“${trimmed.take(160)}${if (trimmed.length > 160) "…" else ""}”"
            appendTranscriptionLog(TestLogLevel.INFO, preview)

            val speedDetail = formatSpeedDetail(audioSeconds, elapsedMs)
            if (speedDetail != null) {
                appendTranscriptionLog(TestLogLevel.OK, "Speed", speedDetail)
            }
            val msg = buildString {
                append("On-device transcription: $preview")
                if (speedDetail != null) append("  ·  $speedDetail")
            }
            _connectionTestState.value = ConnectionTestState.Success(msg)
        } catch (t: Throwable) {
            Log.e(TAG, "Local whisper test failed", t)
            appendTranscriptionLog(
                TestLogLevel.FAIL,
                t.javaClass.simpleName,
                t.message ?: "no message"
            )
            _connectionTestState.value = ConnectionTestState.Error(
                t.message ?: "On-device transcription failed."
            )
        } finally {
            runCatching { ctx.release() }
        }
    }

    /**
     * Decode a 16-bit canonical-PCM mono WAV at 16 kHz into a float32 array
     * normalized to [-1.0, 1.0]. Whisper's input contract. We don't resample
     * — the bundled sample is already 16 kHz mono, and any other rate is
     * caller error for now.
     */
    private fun wavToFloatMono16k(bytes: ByteArray): FloatArray {
        require(bytes.size >= 44) { "WAV too short: ${bytes.size} bytes" }
        val sampleRate = (bytes[24].toInt() and 0xFF) or
            ((bytes[25].toInt() and 0xFF) shl 8) or
            ((bytes[26].toInt() and 0xFF) shl 16) or
            ((bytes[27].toInt() and 0xFF) shl 24)
        val channels = (bytes[22].toInt() and 0xFF) or ((bytes[23].toInt() and 0xFF) shl 8)
        val bits = (bytes[34].toInt() and 0xFF) or ((bytes[35].toInt() and 0xFF) shl 8)
        require(sampleRate == 16000 && channels == 1 && bits == 16) {
            "Unsupported WAV format: sr=$sampleRate ch=$channels bits=$bits — expected 16 kHz mono 16-bit"
        }
        val n = (bytes.size - 44) / 2
        val out = FloatArray(n)
        var di = 44
        for (i in 0 until n) {
            val lo = bytes[di].toInt() and 0xFF
            val hi = bytes[di + 1].toInt()
            val s = (hi shl 8) or lo
            out[i] = s / 32768f
            di += 2
        }
        return out
    }

    private fun appendTranscriptionLog(level: TestLogLevel, message: String, detail: String? = null) {
        _transcriptionTestLog.value = _transcriptionTestLog.value + TestLogEntry(
            level = level, message = message, detail = detail
        )
    }

    /**
     * Sends a sample text through the configured post-processing LLM with the
     * given voice mode prompt and reports the rewritten text. Exercises the
     * exact dynamic-LLM client path that production uses (without recording).
     */
    fun testPostProcessing(voiceMode: VoiceMode) {
        viewModelScope.launch {
            _postProcessingTestState.value = ConnectionTestState.Testing
            _postProcessingTestLog.value = emptyList()
            appendPostProcessingLog(TestLogLevel.RUNNING, "Starting post-processing test")
            try {
                val settings = apiSettings.value
                val llm = settings.llmConfig
                if (llm.provider == LlmProvider.NONE) {
                    appendPostProcessingLog(TestLogLevel.FAIL, "Provider = None", "post-processing disabled")
                    _postProcessingTestState.value = ConnectionTestState.Error(
                        "Post-processing is disabled (provider = None)."
                    )
                    return@launch
                }
                if (llm.modelId.isBlank()) {
                    appendPostProcessingLog(TestLogLevel.FAIL, "Model ID is empty")
                    _postProcessingTestState.value = ConnectionTestState.Error("LLM model is not set.")
                    return@launch
                }
                if (llm.requiresAuth && llm.apiKey.isBlank()) {
                    appendPostProcessingLog(TestLogLevel.FAIL, "API key is missing")
                    _postProcessingTestState.value = ConnectionTestState.Error("LLM API key is missing.")
                    return@launch
                }

                appendPostProcessingLog(
                    TestLogLevel.INFO,
                    "Provider: ${llm.provider.displayName}",
                    "model=${llm.modelId}"
                )

                val sampleText = "um so like, i was thinking we should maybe add some logging to the inference path?"
                val systemPrompt = voiceMode.systemPrompt.ifBlank {
                    "Rewrite the user's text to be clearer and more grammatical. Return only the rewritten text."
                }

                // LOCAL_GEMMA → in-process MediaPipe path. Bypasses HTTP entirely;
                // no separate llama.cpp / ollama server needed.
                if (llm.provider == LlmProvider.LOCAL_GEMMA) {
                    runLocalGemmaPostProcessingTest(
                        modelPath = settings.localModelSettings.gemmaModelPath,
                        systemPrompt = systemPrompt,
                        sampleText = sampleText
                    )
                    return@launch
                }

                appendPostProcessingLog(TestLogLevel.INFO, "Endpoint: ${llm.getBaseUrl()}")
                appendPostProcessingLog(TestLogLevel.INFO, "Voice mode: ${voiceMode.name}")
                appendPostProcessingLog(TestLogLevel.INFO, "Sample input", sampleText)

                val request = ChatCompletionRequest(
                    model = llm.modelId,
                    messages = listOf(
                        ChatMessage(role = "system", content = listOf(ContentPart.TextContent(text = systemPrompt))),
                        ChatMessage(role = "user", content = listOf(ContentPart.TextContent(text = sampleText)))
                    ),
                    modalities = listOf("text")
                )

                appendPostProcessingLog(TestLogLevel.RUNNING, "POST chat/completions")
                val service = withContext(Dispatchers.IO) { createLlmTestClient(llm) }
                val started = System.nanoTime()
                val response = service.chatCompletion(request)
                val elapsedMs = (System.nanoTime() - started) / 1_000_000
                appendPostProcessingLog(
                    TestLogLevel.INFO,
                    "Response HTTP ${response.code()}",
                    "${elapsedMs} ms"
                )

                if (!response.isSuccessful) {
                    val errBody = response.errorBody()?.string()?.take(200).orEmpty()
                    if (errBody.isNotBlank()) {
                        appendPostProcessingLog(TestLogLevel.FAIL, "Error body", errBody)
                    }
                    throw IllegalStateException("HTTP ${response.code()}")
                }
                val out = response.body()?.choices?.firstOrNull()?.message?.content?.trim().orEmpty()
                if (out.isBlank()) {
                    appendPostProcessingLog(TestLogLevel.FAIL, "Empty completion returned")
                    throw IllegalStateException("Empty response")
                }
                val preview = "“${out.take(160)}${if (out.length > 160) "…" else ""}”"
                appendPostProcessingLog(TestLogLevel.OK, "Completion returned", "${out.length} chars")
                appendPostProcessingLog(TestLogLevel.INFO, preview)
                _postProcessingTestState.value = ConnectionTestState.Success("Sample → $preview")
                Log.d(TAG, "Post-processing test ok; len=${out.length}")
            } catch (e: Exception) {
                appendPostProcessingLog(
                    TestLogLevel.FAIL,
                    e.javaClass.simpleName,
                    e.message ?: "no message"
                )
                _postProcessingTestState.value = ConnectionTestState.Error(prettifyError(e))
                Log.e(TAG, "Post-processing test failed", e)
            }
        }
    }

    /**
     * In-process Gemma post-processing test. Loads the user's MediaPipe-
     * converted .bin via [GemmaInferenceEngine] and runs the sample text
     * through it. No HTTP server required.
     */
    private suspend fun runLocalGemmaPostProcessingTest(
        modelPath: String,
        systemPrompt: String,
        sampleText: String
    ) {
        if (modelPath.isBlank()) {
            appendPostProcessingLog(
                TestLogLevel.FAIL,
                "No Gemma model path",
                "Set one in Transcription → Local → Gemma. Expected a MediaPipe-converted .bin (litert-community on HF), not a GGUF file."
            )
            _postProcessingTestState.value = ConnectionTestState.Error(
                "No Gemma model selected. Pick one under Transcription → Local."
            )
            return
        }
        val file = java.io.File(modelPath)
        if (!file.exists()) {
            appendPostProcessingLog(TestLogLevel.FAIL, "Model file missing", modelPath)
            _postProcessingTestState.value = ConnectionTestState.Error(
                "Configured Gemma model file not found on disk."
            )
            return
        }
        appendPostProcessingLog(
            TestLogLevel.INFO,
            "Engine: MediaPipe LLM (in-process)",
            "${file.length() / 1024 / 1024} MB · ${file.name}"
        )
        appendPostProcessingLog(TestLogLevel.INFO, "Sample input", sampleText)
        appendPostProcessingLog(TestLogLevel.RUNNING, "Running in-process Gemma")

        val started = System.nanoTime()
        try {
            val out = withContext(Dispatchers.IO) {
                gemma.rewrite(
                    modelPath = modelPath,
                    systemPrompt = systemPrompt,
                    userText = sampleText
                )
            }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            appendPostProcessingLog(TestLogLevel.OK, "Inference complete", "${elapsedMs} ms")
            if (out.isBlank()) {
                appendPostProcessingLog(TestLogLevel.FAIL, "Empty output")
                _postProcessingTestState.value = ConnectionTestState.Error(
                    "Gemma returned empty text. Model may be incompatible with MediaPipe — confirm it's a litert-community .bin."
                )
                return
            }
            val preview = "“${out.take(160)}${if (out.length > 160) "…" else ""}”"
            appendPostProcessingLog(TestLogLevel.INFO, preview)
            _postProcessingTestState.value = ConnectionTestState.Success("Sample → $preview")
        } catch (t: Throwable) {
            Log.w(TAG, "Local Gemma test failed", t)
            appendPostProcessingLog(
                TestLogLevel.FAIL,
                t.javaClass.simpleName,
                t.message ?: "no message"
            )
            _postProcessingTestState.value = ConnectionTestState.Error(
                t.message ?: "Local Gemma inference failed."
            )
        }
    }

    fun resetPostProcessingTestState() {
        _postProcessingTestState.value = ConnectionTestState.Idle
        _postProcessingTestLog.value = emptyList()
    }

    private fun appendPostProcessingLog(level: TestLogLevel, message: String, detail: String? = null) {
        _postProcessingTestLog.value = _postProcessingTestLog.value + TestLogEntry(
            level = level, message = message, detail = detail
        )
    }

    /** Read the bundled JFK speech sample. Cached on first call. */
    private var sampleSpeechCache: ByteArray? = null
    private fun loadSampleSpeechBytes(): ByteArray {
        sampleSpeechCache?.let { return it }
        val bytes = context.resources.openRawResource(R.raw.sample_speech).use { it.readBytes() }
        sampleSpeechCache = bytes
        return bytes
    }

    /**
     * Parse a canonical WAV PCM header to compute audio duration in seconds.
     * Returns null if the buffer is too short or not a recognizable WAV.
     */
    private fun wavDurationSeconds(bytes: ByteArray): Double? {
        if (bytes.size < 44) return null
        if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
            bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte()) return null
        val channels = (bytes[22].toInt() and 0xFF) or ((bytes[23].toInt() and 0xFF) shl 8)
        val sampleRate = (bytes[24].toInt() and 0xFF) or
            ((bytes[25].toInt() and 0xFF) shl 8) or
            ((bytes[26].toInt() and 0xFF) shl 16) or
            ((bytes[27].toInt() and 0xFF) shl 24)
        val bitsPerSample = (bytes[34].toInt() and 0xFF) or ((bytes[35].toInt() and 0xFF) shl 8)
        val bytesPerSec = sampleRate.toLong() * channels * (bitsPerSample / 8)
        if (bytesPerSec <= 0) return null
        val dataBytes = (bytes.size - 44).coerceAtLeast(0)
        return dataBytes.toDouble() / bytesPerSec
    }

    private fun formatSpeedDetail(audioSeconds: Double?, processingMs: Long): String? {
        if (audioSeconds == null || audioSeconds <= 0.0 || processingMs <= 0L) return null
        val processingSec = processingMs / 1000.0
        val ratio = audioSeconds / processingSec
        return "%.2f× realtime (audio %.2fs / request %.2fs)".format(ratio, audioSeconds, processingSec)
    }

    /** Build a Retrofit client for the configured LLM, mirroring VoiceRepository.createLlmApiService. */
    private fun createLlmTestClient(llm: LlmConfig): ChatCompletionApiService {
        val auth = Interceptor { chain ->
            val rb = chain.request().newBuilder()
            if (llm.requiresAuth && llm.apiKey.isNotEmpty()) {
                when (llm.provider) {
                    LlmProvider.ANTHROPIC -> {
                        rb.addHeader("x-api-key", llm.apiKey)
                        rb.addHeader("anthropic-version", "2023-06-01")
                    }
                    else -> rb.addHeader("Authorization", "Bearer ${llm.apiKey}")
                }
            }
            rb.addHeader("Content-Type", "application/json")
            chain.proceed(rb.build())
        }
        val ok = OkHttpClient.Builder()
            .addInterceptor(auth)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(llm.getBaseUrl())
            .client(ok)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ChatCompletionApiService::class.java)
    }

    private fun prettifyError(e: Throwable): String {
        val msg = e.message.orEmpty()
        return when {
            "401" in msg -> "Authentication failed — check API key."
            "403" in msg -> "Forbidden — verify key permissions."
            "404" in msg -> "Endpoint not found — check base URL and model ID."
            "429" in msg -> "Rate limit exceeded — try again shortly."
            "timeout" in msg -> "Timeout — server didn't respond in time."
            "SSL" in msg || "certificate" in msg -> "SSL/TLS error — check HTTPS configuration."
            "Unable to resolve host" in msg -> "Cannot reach server — check internet connection."
            else -> msg.ifBlank { "Unknown error" }
        }
    }

    /**
     * Export all provider secrets/config as pretty JSON for external integration tests.
     */
    fun buildSecretsExportJson(): String {
        val settings = apiSettings.value
        val providers = ApiProvider.entries.associate { provider ->
            val providerConfig = settings.providerConfigs[provider]
            provider.name to mapOf(
                "displayName" to provider.displayName,
                "baseUrl" to (providerConfig?.customBaseUrl?.ifBlank { provider.defaultEndpoint } ?: provider.defaultEndpoint),
                "requiresAuth" to (providerConfig?.requiresAuth ?: provider.requiresAuth),
                "apiKey" to (settings.apiKeys[provider] ?: ""),
                "modelId" to if (settings.provider == provider) settings.modelId else (provider.defaultModels.firstOrNull() ?: "")
            )
        }

        val payload = mapOf(
            "format" to "hyperwhisper-secrets-v1",
            "currentProvider" to settings.provider.name,
            "providers" to providers
        )
        return GsonBuilder().setPrettyPrinting().create().toJson(payload)
    }

    /**
     * Fetch OpenRouter's catalog (`/api/v1/models`). Sets [openRouterModels],
     * [openRouterRefreshing], [openRouterError]. Each entry carries flags so
     * the UI can apply free/transcription filters client-side.
     */
    fun refreshOpenRouterModels() {
        viewModelScope.launch {
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
                    OpenRouterModelInfo(
                        id = id,
                        displayName = name,
                        isFree = free,
                        supportsAudio = supportsAudio,
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

    fun startWhisperDownload(modelId: String) {
        val entry = WhisperModelCatalog.byId(modelId) ?: return
        whisperDownloader.start(entry)
    }

    fun cancelWhisperDownload(modelId: String) {
        whisperDownloader.cancel(modelId)
    }

    fun deleteDownloadedWhisperModel(modelId: String) {
        val entry = WhisperModelCatalog.byId(modelId) ?: return
        val activePath = apiSettings.value.localModelSettings.whisperModelPath
        val targetPath = whisperDownloader.targetFile(entry).absolutePath
        whisperDownloader.delete(entry)
        viewModelScope.launch {
            if (activePath == targetPath) {
                val cleared = apiSettings.value.localModelSettings.copy(
                    whisperModelPath = "",
                    whisperModelHash = "",
                    useLocalWhisper = false
                )
                settingsRepository.updateLocalModelSettings(cleared)
            }
            discoverModels()
        }
    }

    /** Mark the cloud configuration (current `apiSettings.provider`) as the active source. */
    fun setActiveCloudProvider() {
        viewModelScope.launch {
            val s = apiSettings.value.localModelSettings
            if (s.useLocalWhisper) {
                settingsRepository.updateLocalModelSettings(s.copy(useLocalWhisper = false))
            }
        }
    }

    /** Mark a specific local Whisper model as active (and switch to local mode). */
    fun setActiveLocalWhisperModel(path: String) {
        viewModelScope.launch {
            val s = apiSettings.value.localModelSettings
            settingsRepository.updateLocalModelSettings(
                s.copy(whisperModelPath = path, useLocalWhisper = true)
            )
        }
    }

    /**
     * Clear all API call logs
     */
    fun clearApiCallLogs() {
        viewModelScope.launch {
            settingsRepository.clearApiCallLogs()
            Log.d(TAG, "Cleared all API call logs")
        }
    }
}
