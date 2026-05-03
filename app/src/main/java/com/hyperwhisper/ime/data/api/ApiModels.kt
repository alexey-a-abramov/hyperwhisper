package com.hyperwhisper.data

/**
 * Domain Models
 */
data class Language(
    val code: String, // ISO-639-1 code
    val name: String,
    val isRTL: Boolean = false // Right-to-Left language support
)

// Supported languages list - sorted alphabetically with Auto-detect first, English second
val SUPPORTED_LANGUAGES = listOf(
    Language("", "Auto-detect"),
    Language("en", "English"),
    Language("af", "Afrikaans"),
    Language("sq", "Albanian"),
    Language("am", "Amharic"),
    Language("ar", "Arabic", isRTL = true),
    Language("hy", "Armenian"),
    Language("az", "Azerbaijani"),
    Language("eu", "Basque"),
    Language("be", "Belarusian"),
    Language("bn", "Bengali"),
    Language("bs", "Bosnian"),
    Language("bg", "Bulgarian"),
    Language("my", "Burmese"),
    Language("ca", "Catalan"),
    Language("ceb", "Cebuano"),
    Language("zh", "Chinese (Mandarin)"),
    Language("hr", "Croatian"),
    Language("cs", "Czech"),
    Language("da", "Danish"),
    Language("nl", "Dutch"),
    Language("et", "Estonian"),
    Language("fi", "Finnish"),
    Language("fr", "French"),
    Language("gl", "Galician"),
    Language("ka", "Georgian"),
    Language("de", "German"),
    Language("el", "Greek"),
    Language("gu", "Gujarati"),
    Language("ha", "Hausa"),
    Language("he", "Hebrew", isRTL = true),
    Language("hi", "Hindi"),
    Language("hu", "Hungarian"),
    Language("is", "Icelandic"),
    Language("ig", "Igbo"),
    Language("id", "Indonesian"),
    Language("ga", "Irish"),
    Language("it", "Italian"),
    Language("ja", "Japanese"),
    Language("jv", "Javanese"),
    Language("kn", "Kannada"),
    Language("kk", "Kazakh"),
    Language("km", "Khmer"),
    Language("ko", "Korean"),
    Language("ky", "Kyrgyz"),
    Language("lo", "Lao"),
    Language("lv", "Latvian"),
    Language("lt", "Lithuanian"),
    Language("mk", "Macedonian"),
    Language("ms", "Malay"),
    Language("ml", "Malayalam"),
    Language("mt", "Maltese"),
    Language("mr", "Marathi"),
    Language("mn", "Mongolian"),
    Language("ne", "Nepali"),
    Language("no", "Norwegian"),
    Language("or", "Odia"),
    Language("fa", "Persian (Farsi)", isRTL = true),
    Language("pl", "Polish"),
    Language("pt", "Portuguese"),
    Language("pa", "Punjabi"),
    Language("ro", "Romanian"),
    Language("ru", "Russian"),
    Language("sr", "Serbian"),
    Language("si", "Sinhala"),
    Language("sk", "Slovak"),
    Language("sl", "Slovenian"),
    Language("so", "Somali"),
    Language("es", "Spanish"),
    Language("sw", "Swahili"),
    Language("sv", "Swedish"),
    Language("tl", "Tagalog"),
    Language("tg", "Tajik"),
    Language("ta", "Tamil"),
    Language("te", "Telugu"),
    Language("th", "Thai"),
    Language("tr", "Turkish"),
    Language("tk", "Turkmen"),
    Language("uk", "Ukrainian"),
    Language("ur", "Urdu", isRTL = true),
    Language("uz", "Uzbek"),
    Language("vi", "Vietnamese"),
    Language("cy", "Welsh"),
    Language("xh", "Xhosa"),
    Language("yo", "Yoruba"),
    Language("zu", "Zulu")
)

data class ProviderConfig(
    val customBaseUrl: String = "", // Empty means use default
    val requiresAuth: Boolean = true // Can be toggled per provider
)

data class ApiSettings(
    val provider: ApiProvider = ApiProvider.OPENAI,
    val baseUrl: String = "", // Deprecated - kept for migration, use providerConfigs instead
    val apiKeys: Map<ApiProvider, String> = emptyMap(), // Per-provider API keys
    val providerConfigs: Map<ApiProvider, ProviderConfig> = emptyMap(), // Per-provider configuration
    val modelId: String = "whisper-1",
    val inputLanguage: String = "", // ISO-639-1 code for speech input - empty for auto-detect
    val outputLanguage: String = "", // ISO-639-1 code for output - empty to keep original
    val llmConfig: LlmConfig = LlmConfig(), // LLM configuration for post-processing
    val localModelSettings: LocalModelSettings = LocalModelSettings() // Local model configuration
) {
    // Helper to get API key for current provider
    fun getCurrentApiKey(): String = apiKeys[provider] ?: ""

    // Helper to get base URL for current provider
    fun getCurrentBaseUrl(): String {
        val config = providerConfigs[provider]
        return config?.customBaseUrl?.ifEmpty { provider.defaultEndpoint }
            ?: baseUrl.ifEmpty { provider.defaultEndpoint }
    }

    // Helper to check if current provider requires auth
    fun getCurrentRequiresAuth(): Boolean {
        val config = providerConfigs[provider]
        return config?.requiresAuth ?: provider.requiresAuth
    }
}

/**
 * LLM providers for post-processing/transformation
 */
enum class LlmProvider(
    val displayName: String,
    val defaultEndpoint: String,
    val defaultModels: List<String>,
    val requiresAuth: Boolean = true
) {
    NONE(
        displayName = "None (Verbatim Only)",
        defaultEndpoint = "",
        defaultModels = listOf("none"),
        requiresAuth = false
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultEndpoint = "https://api.openai.com/v1/",
        defaultModels = listOf("gpt-4.1", "gpt-4.1-mini", "gpt-4o", "gpt-4o-mini"),
        requiresAuth = true
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultEndpoint = "https://api.deepseek.com/v1/",
        defaultModels = listOf("deepseek-chat", "deepseek-reasoner"),
        requiresAuth = true
    ),
    GEMINI(
        displayName = "Google Gemini",
        defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta/",
        defaultModels = listOf(
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemma-4-27b-it",
            "gemma-4-9b-it",
            "gemini-1.5-pro"
        ),
        requiresAuth = true
    ),
    ANTHROPIC(
        displayName = "Anthropic Claude",
        defaultEndpoint = "https://api.anthropic.com/v1/",
        defaultModels = listOf("claude-opus-4-7", "claude-sonnet-4-6", "claude-haiku-4-5-20251001"),
        requiresAuth = true
    ),
    MISTRAL(
        displayName = "Mistral AI",
        defaultEndpoint = "https://api.mistral.ai/v1/",
        defaultModels = listOf("mistral-large-latest", "mistral-small-latest", "pixtral-large-latest"),
        requiresAuth = true
    ),
    GROQ(
        displayName = "Groq",
        defaultEndpoint = "https://api.groq.com/openai/v1/",
        defaultModels = listOf("llama-3.3-70b-versatile", "llama-3.1-70b-versatile", "llama-3.1-8b-instant"),
        requiresAuth = true
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultEndpoint = "https://openrouter.ai/api/v1/",
        defaultModels = listOf(
            "google/gemini-2.0-flash-exp:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "deepseek/deepseek-chat:free"
        ),
        requiresAuth = true
    ),
    OPENAI_COMPATIBLE(
        displayName = "OpenAI-Compatible",
        defaultEndpoint = "http://localhost:8080/v1/",
        defaultModels = listOf("gpt-3.5-turbo", "gpt-4"),
        requiresAuth = false
    ),
    LOCAL_GEMMA(
        displayName = "Local Gemma",
        defaultEndpoint = "http://127.0.0.1:8081/v1/",
        defaultModels = listOf(
            "gemma-4-2b-it",
            "gemma-4-9b-it",
            "gemma-4-27b-it",
            "gemma-3n-E2B-it",
            "google/gemma-2-9b-it"
        ),
        requiresAuth = false
    ),
    LOCAL_LLAMACPP(
        displayName = "Local llama.cpp Server",
        // llama-server's zero-config default is 127.0.0.1:8080. Editable in UI.
        defaultEndpoint = "http://127.0.0.1:8080/v1/",
        // Model IDs match the typical GGUF filename stems users see in
        // /sdcard/LLM/. The actual ID accepted depends on llama-server's
        // --alias flag (defaults to the file stem). Editable in UI.
        defaultModels = listOf(
            "qwen2.5-14b-instruct-q4_k_m",
            "llama-3.1-8b-instruct-q6_k"
        ),
        requiresAuth = false
    )
}

/**
 * LLM configuration for post-processing
 */
data class LlmConfig(
    val provider: LlmProvider = LlmProvider.OPENAI,
    val customBaseUrl: String = "", // Empty means use default
    val apiKey: String = "",
    val modelId: String = "gpt-4o-mini",
    val requiresAuth: Boolean = true
) {
    fun getBaseUrl(): String = customBaseUrl.ifEmpty { provider.defaultEndpoint }
}

enum class ApiProvider(
    val displayName: String,
    val defaultEndpoint: String,
    val defaultModels: List<String>,
    val requiresAuth: Boolean = true
) {
    OPENAI(
        displayName = "OpenAI Whisper",
        defaultEndpoint = "https://api.openai.com/v1/",
        defaultModels = listOf("gpt-4o-transcribe", "gpt-4o-mini-transcribe", "whisper-1"),
        requiresAuth = true
    ),
    DEEPGRAM(
        displayName = "Deepgram",
        defaultEndpoint = "https://api.deepgram.com/v1/",
        defaultModels = listOf("nova-3", "nova-2", "whisper-large"),
        requiresAuth = true
    ),
    ASSEMBLYAI(
        displayName = "AssemblyAI",
        defaultEndpoint = "https://api.assemblyai.com/v2/",
        defaultModels = listOf("best", "nano"),
        requiresAuth = true
    ),
    GOOGLE_CLOUD(
        displayName = "Google Cloud Speech",
        defaultEndpoint = "https://speech.googleapis.com/v1/",
        defaultModels = listOf("chirp", "long", "phone_call", "video", "command_and_search", "default"),
        requiresAuth = true
    ),
    AWS_TRANSCRIBE(
        displayName = "AWS Transcribe",
        defaultEndpoint = "https://transcribe.us-east-1.amazonaws.com/",
        defaultModels = listOf("standard", "medical", "call-analytics"),
        requiresAuth = true
    ),
    AZURE_SPEECH(
        displayName = "Azure AI Speech",
        defaultEndpoint = "https://eastus.stt.speech.microsoft.com/",
        defaultModels = listOf("default", "conversation", "dictation", "interactive"),
        requiresAuth = true
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultEndpoint = "https://api.deepseek.com/v1/",
        defaultModels = listOf("deepseek-chat", "deepseek-reasoner"),
        requiresAuth = true
    ),
    MISTRAL(
        displayName = "Mistral Voxtral",
        defaultEndpoint = "https://api.mistral.ai/v1/",
        defaultModels = listOf("voxtral-mini-latest", "voxtral-small-latest"),
        requiresAuth = true
    ),
    REVAI(
        displayName = "Rev.ai",
        defaultEndpoint = "https://api.rev.ai/speechtotext/v1/",
        defaultModels = listOf("rev", "rev_human_fallback"),
        requiresAuth = true
    ),
    GROQ(
        displayName = "Groq Whisper",
        defaultEndpoint = "https://api.groq.com/openai/v1/",
        defaultModels = listOf("whisper-large-v3", "whisper-large-v3-turbo", "distil-whisper-large-v3-en"),
        requiresAuth = true
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultEndpoint = "https://openrouter.ai/api/v1/",
        // OpenRouter routes transcription via /chat/completions with input_audio,
        // not the OpenAI multipart /audio/transcriptions endpoint. Voxtral is
        // currently the only audio-capable model in their catalog.
        defaultModels = listOf("mistralai/voxtral-small-24b-2507"),
        requiresAuth = true
    ),
    GEMINI(
        displayName = "Google Gemini",
        defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta/",
        defaultModels = listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash"),
        requiresAuth = true
    ),
    ANTIGRAVITY(
        displayName = "Google Antigravity (OAuth)",
        defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta/openai/",
        defaultModels = listOf("gemini-2.5-flash", "gemini-2.0-flash"),
        requiresAuth = false
    ),
    HUGGINGFACE(
        displayName = "Hugging Face",
        defaultEndpoint = "https://api-inference.huggingface.co/",
        defaultModels = listOf("openai/whisper-large-v3", "openai/whisper-large-v3-turbo", "openai/whisper-medium"),
        requiresAuth = true
    ),
    SELFHOSTED_WHISPER(
        displayName = "Self-hosted Whisper",
        defaultEndpoint = "http://127.0.0.1:8080/",
        defaultModels = listOf("base.en", "small.en", "medium.en"),
        requiresAuth = false
    ),
    LOCAL_WHISPER(
        displayName = "Local Whisper (.bin)",
        defaultEndpoint = "local",
        defaultModels = listOf("tiny.en", "tiny", "base.en", "base", "small.en", "small"),
        requiresAuth = false
    );

    /**
     * True when the provider requires us to dispatch transcription via
     * /chat/completions with an `input_audio` content block instead of the
     * OpenAI multipart `/audio/transcriptions` endpoint. Keeps the test path
     * (SettingsViewModel) and production strategy (VoiceRepository) aligned.
     */
    fun usesChatAudioForTranscription(): Boolean = when (this) {
        OPENROUTER, GEMINI, ANTIGRAVITY -> true
        else -> false
    }
}
