package com.hyperwhisper.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.hyperwhisper.R
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.ChatCompletionRequest
import com.hyperwhisper.data.ChatMessage
import com.hyperwhisper.data.ContentPart
import com.hyperwhisper.data.LlmConfig
import com.hyperwhisper.data.LlmProvider
import com.hyperwhisper.data.LocalModelSettings
import com.hyperwhisper.data.SettingsRepository
import com.hyperwhisper.data.VoiceMode
import com.hyperwhisper.ui.settings.ConnectionTestState
import com.hyperwhisper.ui.settings.TestLogEntry
import com.hyperwhisper.ui.settings.TestLogLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the diagnostic "Test connection" / "Test post-processing" probes invoked
 * from the Settings UI. Extracted from [com.hyperwhisper.ui.settings.SettingsViewModel]
 * so the VM stays focused on settings persistence and orchestration.
 *
 * Exposes immutable [StateFlow]s the VM re-exposes verbatim. Each test entry
 * point ([testConnection], [testPostProcessing]) clears the relevant log/state
 * flow on entry, so callers do not need to do that themselves.
 *
 * Note: as a `@Singleton`, the test state survives VM recreation (e.g. screen
 * rotation). That's a small improvement over the previous per-VM behaviour
 * — each test invocation still starts from a clean slate.
 */
@Singleton
class ConnectionTester @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val transcriptionApiService: TranscriptionApiService,
    private val gson: Gson,
    private val localLlm: com.hyperwhisper.ime.llm.LocalLlmRouter,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ConnectionTester"
    }

    private val _connectionTestState =
        MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    private val _postProcessingTestState =
        MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val postProcessingTestState: StateFlow<ConnectionTestState> =
        _postProcessingTestState.asStateFlow()

    private val _transcriptionTestLog = MutableStateFlow<List<TestLogEntry>>(emptyList())
    val transcriptionTestLog: StateFlow<List<TestLogEntry>> = _transcriptionTestLog.asStateFlow()

    private val _postProcessingTestLog = MutableStateFlow<List<TestLogEntry>>(emptyList())
    val postProcessingTestLog: StateFlow<List<TestLogEntry>> = _postProcessingTestLog.asStateFlow()

    fun resetConnectionTestState() {
        _connectionTestState.value = ConnectionTestState.Idle
        _transcriptionTestLog.value = emptyList()
    }

    fun resetPostProcessingTestState() {
        _postProcessingTestState.value = ConnectionTestState.Idle
        _postProcessingTestLog.value = emptyList()
    }

    /**
     * State of an individual provider row during a retest-all run. The
     * orchestrator advances each row PENDING → RUNNING → OK / ERROR. After
     * a successful test the per-provider lastTestedAt timestamp is also
     * refreshed (via the existing test functions' success path), so picker
     * badges reflect the new state without any extra plumbing here.
     */
    sealed class RetestRowState {
        object Pending : RetestRowState()
        object Running : RetestRowState()
        data class Ok(val message: String?) : RetestRowState()
        data class Error(val message: String?) : RetestRowState()
    }

    private val _retestProgress = MutableStateFlow<Map<String, RetestRowState>>(emptyMap())
    val retestProgress: StateFlow<Map<String, RetestRowState>> = _retestProgress.asStateFlow()

    private val _retestRunning = MutableStateFlow(false)
    val retestRunning: StateFlow<Boolean> = _retestRunning.asStateFlow()

    /** Stable key for the progress map. Distinct namespaces for ASR / LLM
     *  so an ApiProvider.OPENAI row doesn't collide with LlmProvider.OPENAI. */
    fun asrKey(p: ApiProvider): String = "asr:${p.name}"
    fun llmKey(p: LlmProvider): String = "llm:${p.name}"

    fun resetRetestProgress() {
        _retestProgress.value = emptyMap()
    }

    /**
     * Sequentially re-test every configured ASR + LLM provider, refreshing
     * the per-provider lastTestedAt timestamps that the picker badges read.
     *
     * Sequential rather than parallel: the existing test-flow side-effects
     * (single-test state flow + log entries) aren't reentrancy-safe, and
     * fanning out cloud + local provider tests in parallel risks OOM on
     * mid-range devices when LOCAL_WHISPER and LOCAL_GEMMA both load model
     * files. Per-row live progress is exposed via [retestProgress].
     *
     * "Configured" mirrors the picker filter from roadmap A: providers with
     * a stored key OR providers that don't require auth. Local providers
     * are skipped if their model file isn't present (the test would
     * predictably fail with no actionable signal for the user).
     */
    suspend fun retestAll() {
        if (_retestRunning.value) return
        _retestRunning.value = true
        try {
            val settings = settingsRepository.apiSettings.first()

            val asrTargets = ApiProvider.entries.filter { it.isConfiguredForRetest(settings) }
            val llmTargets = LlmProvider.entries
                .filter { it != LlmProvider.NONE }
                .filter { it.isConfiguredForRetest(settings) }

            val initial = buildMap<String, RetestRowState> {
                asrTargets.forEach { put(asrKey(it), RetestRowState.Pending) }
                llmTargets.forEach { put(llmKey(it), RetestRowState.Pending) }
            }
            _retestProgress.value = initial

            for (provider in asrTargets) {
                _retestProgress.value = _retestProgress.value +
                    (asrKey(provider) to RetestRowState.Running)
                val key = settings.apiKeys[provider].orEmpty()
                val baseUrl = settings.providerConfigs[provider]?.customBaseUrl
                    ?.takeIf { it.isNotEmpty() } ?: provider.defaultEndpoint
                val modelId = provider.defaultModels.firstOrNull().orEmpty()

                resetConnectionTestState()
                runCatching { testConnection(provider, baseUrl, key, modelId) }
                _retestProgress.value = _retestProgress.value + (asrKey(provider) to
                    when (val state = _connectionTestState.value) {
                        is ConnectionTestState.Success -> RetestRowState.Ok(state.message)
                        is ConnectionTestState.Error -> RetestRowState.Error(state.message)
                        else -> RetestRowState.Error("Test ended in unexpected state")
                    })
            }

            for (provider in llmTargets) {
                _retestProgress.value = _retestProgress.value +
                    (llmKey(provider) to RetestRowState.Running)
                val key = settings.llmConfig.apiKeys[provider].orEmpty()
                val perCfg = settings.llmConfig.providerConfigs[provider]
                val baseUrl = perCfg?.customBaseUrl?.takeIf { it.isNotEmpty() }
                    ?: provider.defaultEndpoint
                val requiresAuth = perCfg?.requiresAuth ?: provider.requiresAuth
                val modelId = provider.defaultModels.firstOrNull().orEmpty()
                val targetLlm = LlmConfig(
                    provider = provider,
                    customBaseUrl = baseUrl,
                    apiKey = key,
                    modelId = modelId,
                    requiresAuth = requiresAuth,
                )

                resetPostProcessingTestState()
                val syntheticVoiceMode = VoiceMode(
                    id = "__retest_all__",
                    name = "retest-all",
                    systemPrompt = "",
                )
                runCatching { testPostProcessing(syntheticVoiceMode, overrideLlm = targetLlm) }
                _retestProgress.value = _retestProgress.value + (llmKey(provider) to
                    when (val state = _postProcessingTestState.value) {
                        is ConnectionTestState.Success -> RetestRowState.Ok(state.message)
                        is ConnectionTestState.Error -> RetestRowState.Error(state.message)
                        else -> RetestRowState.Error("Test ended in unexpected state")
                    })
            }
        } finally {
            _retestRunning.value = false
            // Leave the existing single-test flows in Idle so the per-section
            // overlays don't show the last batched provider's result on next
            // open.
            resetConnectionTestState()
            resetPostProcessingTestState()
        }
    }

    private fun ApiProvider.isConfiguredForRetest(settings: ApiSettings): Boolean {
        if (this == ApiProvider.LOCAL_WHISPER) {
            // Local model file path lives in localModelSettings; skip if blank
            // or missing on disk so the test doesn't fail for an unreachable-
            // by-design reason.
            val path = settings.localModelSettings.whisperModelPath
            return path.isNotBlank() && java.io.File(path).exists()
        }
        return settings.apiKeys[this]?.isNotEmpty() == true || !this.requiresAuth
    }

    private fun LlmProvider.isConfiguredForRetest(settings: ApiSettings): Boolean {
        if (this == LlmProvider.LOCAL_GEMMA) {
            val path = settings.localModelSettings.gemmaModelPath
            return path.isNotBlank() && java.io.File(path).exists()
        }
        return settings.llmConfig.apiKeys[this]?.isNotEmpty() == true || !this.requiresAuth
    }

    /**
     * Sends a real bundled speech sample (JFK clip, ~12s) to the configured
     * transcription endpoint and reports back the transcribed text. Emits
     * step-by-step entries to [transcriptionTestLog] so the UI can show
     * progress live.
     */
    suspend fun testConnection(
        provider: ApiProvider,
        baseUrl: String,
        apiKey: String,
        modelId: String
    ) {
        _connectionTestState.value = ConnectionTestState.Testing
        _transcriptionTestLog.value = emptyList()
        appendTranscriptionLog(TestLogLevel.RUNNING, "Starting transcription test")

        // Route by provider, not the global useLocalWhisper toggle: the toggle
        // governs production routing, but a Settings-side test should exercise
        // the provider the user explicitly asked to test. Retest-all relies on
        // this: it iterates per-provider and would otherwise short-circuit to
        // local for every cloud provider whenever the toggle is on.
        val local = settingsRepository.apiSettings.first().localModelSettings
        if (provider == ApiProvider.LOCAL_WHISPER) {
            runLocalWhisperTest(local)
            return
        }

        appendTranscriptionLog(TestLogLevel.INFO, "Provider: ${provider.displayName}", "model=$modelId")
        appendTranscriptionLog(TestLogLevel.INFO, "Endpoint: $baseUrl")

        if (provider.usesChatAudioForTranscription()) {
            runChatAudioTranscriptionTest(provider, baseUrl, apiKey, modelId)
            return
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
                        settingsRepository.recordProviderTested(provider, System.currentTimeMillis())
                        _connectionTestState.value = ConnectionTestState.Success(
                            "Local whisper.cpp server is responding."
                        )
                        return
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
            settingsRepository.recordProviderTested(provider, System.currentTimeMillis())
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
            settingsRepository.recordProviderTested(provider, System.currentTimeMillis())
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
     * file and bundled sample without dispatching to a cloud endpoint.
     */
    private suspend fun runLocalWhisperTest(local: LocalModelSettings) {
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
            settingsRepository.recordProviderTested(
                ApiProvider.LOCAL_WHISPER, System.currentTimeMillis()
            )
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
     * normalized to [-1.0, 1.0]. Whisper's input contract.
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
     *
     * [overrideLlm] lets retest-all aim the test at a specific LLM provider
     * (with that provider's stored key/baseUrl/auth) without mutating the
     * active selection. When null, the active LlmConfig is used.
     */
    suspend fun testPostProcessing(
        voiceMode: VoiceMode,
        overrideLlm: LlmConfig? = null,
    ) {
        _postProcessingTestState.value = ConnectionTestState.Testing
        _postProcessingTestLog.value = emptyList()
        appendPostProcessingLog(TestLogLevel.RUNNING, "Starting post-processing test")
        try {
            val settings = settingsRepository.apiSettings.first()
            val llm = overrideLlm ?: settings.llmConfig
            if (llm.provider == LlmProvider.NONE) {
                appendPostProcessingLog(TestLogLevel.FAIL, "Provider = None", "post-processing disabled")
                _postProcessingTestState.value = ConnectionTestState.Error(
                    "Post-processing is disabled (provider = None)."
                )
                return
            }
            if (llm.modelId.isBlank()) {
                appendPostProcessingLog(TestLogLevel.FAIL, "Model ID is empty")
                _postProcessingTestState.value = ConnectionTestState.Error("LLM model is not set.")
                return
            }
            if (llm.requiresAuth && llm.apiKey.isBlank()) {
                appendPostProcessingLog(TestLogLevel.FAIL, "API key is missing")
                _postProcessingTestState.value = ConnectionTestState.Error("LLM API key is missing.")
                return
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
                return
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
            settingsRepository.recordLlmProviderTested(llm.provider, System.currentTimeMillis())
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
        val engineLabel = when (localLlm.engineFor(modelPath)) {
            com.hyperwhisper.ime.llm.LocalLlmRouter.Engine.LLAMA_CPP -> "Engine: llama.cpp (in-process, GGUF)"
            com.hyperwhisper.ime.llm.LocalLlmRouter.Engine.GEMMA -> "Engine: MediaPipe LLM (in-process)"
        }
        appendPostProcessingLog(
            TestLogLevel.INFO,
            engineLabel,
            "${file.length() / 1024 / 1024} MB · ${file.name}"
        )
        appendPostProcessingLog(TestLogLevel.INFO, "Sample input", sampleText)
        appendPostProcessingLog(TestLogLevel.RUNNING, "Running in-process inference")

        val started = System.nanoTime()
        try {
            val out = withContext(Dispatchers.IO) {
                localLlm.rewrite(
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
                    "Local LLM returned empty text. The model may be malformed for its runtime — for .gguf use a llama.cpp-compatible quant; for .task/.litertlm/.bin use a MediaPipe-converted model from huggingface.co/litert-community."
                )
                return
            }
            val preview = "“${out.take(160)}${if (out.length > 160) "…" else ""}”"
            appendPostProcessingLog(TestLogLevel.INFO, preview)
            settingsRepository.recordLlmProviderTested(
                LlmProvider.LOCAL_GEMMA, System.currentTimeMillis()
            )
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

    private fun appendPostProcessingLog(level: TestLogLevel, message: String, detail: String? = null) {
        _postProcessingTestLog.value = _postProcessingTestLog.value + TestLogEntry(
            level = level, message = message, detail = detail
        )
    }

    /** Read the bundled JFK speech sample. Cached on first call. */
    @Volatile
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

    private suspend fun prettifyError(e: Throwable): String {
        val strings = com.hyperwhisper.localization.stringsFor(
            settingsRepository.appearanceSettings.first().uiLanguage
        )
        val msg = e.message.orEmpty()
        return when {
            "401" in msg -> strings.authenticationFailed
            "403" in msg -> strings.errorForbidden
            "404" in msg -> strings.endpointNotFound
            "429" in msg -> strings.errorRateLimit
            "timeout" in msg -> strings.connectionTimeout
            "SSL" in msg || "certificate" in msg -> strings.sslError
            "Unable to resolve host" in msg -> strings.errorNetworkFailed
            else -> msg.ifBlank { strings.errorUnknown }
        }
    }
}
