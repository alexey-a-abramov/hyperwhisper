package com.hyperwhisper.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.google.gson.annotations.SerializedName
import java.io.File

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

data class VoiceMode(
    val id: String,
    val name: String,
    val description: String = "",
    val systemPrompt: String = "",
    val model: String = "whisper-1",
    val processingMode: String = "direct",
    val isBuiltIn: Boolean = false,
    val inputLanguageHint: String = "" // Hint for input language if model supports it
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

/**
 * Local model information for discovery and integrity
 */
data class LocalModelInfo(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val hash: String = "",
    val type: LocalModelType = LocalModelType.WHISPER,
    val isVerified: Boolean = false
)

enum class LocalModelType {
    WHISPER,
    GEMMA,
    LLAMA
}

data class LocalModelSettings(
    val whisperModelPath: String = "",
    val gemmaModelPath: String = "",
    val whisperModelHash: String = "",
    val gemmaModelHash: String = "",
    val useLocalWhisper: Boolean = false,
    val useLocalGemma: Boolean = false,
    val threads: Int = 4,
    val autoDiscover: Boolean = true
)

enum class RecordingState {
    IDLE,
    RECORDING,
    RECORDING_COMPLETE_AWAITING_CONFIRMATION, // Recording finished, waiting for user confirmation (30+ sec)
    PROCESSING,
    ERROR
}

/**
 * More granular processing phases for better visual feedback
 */
enum class ProcessingPhase {
    IDLE,
    PREPARING_AUDIO,     // Converting and preparing audio file
    SENDING_TO_SERVER,   // Uploading audio to API
    WAITING_FOR_RESPONSE, // Waiting for transcription response
    RECEIVING_DATA,      // Receiving and parsing response
    COMPLETE,
    ERROR
}

/**
 * Processing stage for more detailed progress tracking
 */
enum class ProcessingStage(val displayName: String, val progressStart: Float, val progressEnd: Float) {
    PREPARING("Preparing audio...", 0.0f, 0.1f),
    CONVERTING_AUDIO("Converting audio...", 0.1f, 0.2f),
    LOADING_MODEL("Loading model...", 0.2f, 0.3f),
    TRANSCRIBING("Transcribing...", 0.3f, 0.8f),
    POST_PROCESSING("Post-processing...", 0.8f, 0.95f),
    UPLOADING("Uploading audio...", 0.1f, 0.3f),
    WAITING_API("Waiting for API...", 0.3f, 0.9f),
    FINISHING("Finishing...", 0.95f, 1.0f)
}

/**
 * API Request/Response DTOs
 */

// Strategy A: Transcription (Whisper-style)
data class TranscriptionRequest(
    @SerializedName("file") val file: String,
    @SerializedName("model") val model: String,
    @SerializedName("response_format") val responseFormat: String = "text"
)

data class TranscriptionResponse(
    @SerializedName("text") val text: String,
    @SerializedName("duration") val duration: Double? = null,
    @SerializedName("words") val words: List<WordInfo>? = null,
    @SerializedName("usage") val usage: TokenUsage? = null
)

data class WordInfo(
    @SerializedName("word") val word: String,
    @SerializedName("start") val start: Double,
    @SerializedName("end") val end: Double
)

// Strategy B: Chat Completion with Audio
data class ChatCompletionRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<ChatMessage>,
    @SerializedName("modalities") val modalities: List<String> = listOf("text", "audio")
)

data class ChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: List<ContentPart>
)

sealed class ContentPart {
    data class TextContent(
        @SerializedName("type") val type: String = "text",
        @SerializedName("text") val text: String
    ) : ContentPart()

    data class AudioContent(
        @SerializedName("type") val type: String = "input_audio",
        @SerializedName("input_audio") val inputAudio: InputAudio
    ) : ContentPart()
}

data class InputAudio(
    @SerializedName("data") val data: String, // Base64
    @SerializedName("format") val format: String = "wav"
)

data class ChatCompletionResponse(
    @SerializedName("id") val id: String,
    @SerializedName("choices") val choices: List<Choice>,
    @SerializedName("usage") val usage: TokenUsage? = null
)

data class Choice(
    @SerializedName("message") val message: ResponseMessage,
    @SerializedName("finish_reason") val finishReason: String
)

data class ResponseMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

/**
 * Processing information for transparency
 */
data class ProcessingInfo(
    val processingMode: String, // "single-step" or "two-step"
    val strategy: String, // "transcription" or "chat-completion"
    val transcriptionModel: String, // Model used for transcription
    val postProcessingModel: String? = null, // Model used for post-processing (null if single-step)
    val translationEnabled: Boolean = false, // Whether translation was applied
    val translationTarget: String? = null, // Target language for translation
    val originalTranscription: String? = null, // Original text before post-processing (null if single-step)
    val voiceModeName: String, // Name of voice mode used
    val systemPrompt: String, // System prompt that was used
    val audioDurationSeconds: Double = 0.0, // Audio duration in seconds
    val transcriptionTokens: TokenUsage? = null, // Tokens used for transcription
    val postProcessingTokens: TokenUsage? = null, // Tokens used for post-processing (if applicable)
    val processingTimeMs: Long = 0L, // Total processing time in milliseconds
    val transcriptionTimeMs: Long? = null, // Time for transcription step in milliseconds
    val postProcessingTimeMs: Long? = null, // Time for post-processing step in milliseconds
    val audioFileSizeBytes: Long = 0L, // Audio file size in bytes
    val timestamp: Long = System.currentTimeMillis() // When processing started
)

/**
 * Result wrapper for API calls
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T, val processingInfo: ProcessingInfo? = null) : ApiResult<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}

/**
 * Appearance Settings
 */

// Color scheme options with environment themes
// All colors optimized for WCAG AA/AAA contrast ratios (4.5:1 minimum, 7:1 preferred)
// Ensures excellent readability on both light and dark backgrounds
enum class ColorSchemeOption(
    val displayName: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val tertiaryColor: Color
) {
    // Classic themes with improved contrast
    TERMINAL_DARK("Terminal Dark", Color(0xFF2C2C2C), Color(0xFF00D000), Color(0xFF0099FF)),
    OCEAN_DEEP("Ocean Deep", Color(0xFF005577), Color(0xFF0099CC), Color(0xFF66CCFF)),
    FOREST_NIGHT("Forest Night", Color(0xFF1B4D0E), Color(0xFF2E7D1E), Color(0xFF5CB85C)),
    SUNSET_HORIZON("Sunset Horizon", Color(0xFFCC5500), Color(0xFFDD7700), Color(0xFFFF9933)),
    ARCTIC_FROST("Arctic Frost", Color(0xFF3380AA), Color(0xFF5599CC), Color(0xFF88BBEE)),
    DESERT_STORM("Desert Storm", Color(0xFFAA8855), Color(0xFFCC9966), Color(0xFFDDBB88)),
    NEON_CITY("Neon City", Color(0xFFCC00CC), Color(0xFF00CCCC), Color(0xFFCCCC00)),
    CHERRY_BLOSSOM("Cherry Blossom", Color(0xFFDD88AA), Color(0xFFEE99BB), Color(0xFFFFAACC)),
    MIDNIGHT_SKY("Midnight Sky", Color(0xFF1A1A5C), Color(0xFF3B3B82), Color(0xFF5555AA)),
    LAVA_FLOW("Lava Flow", Color(0xFFCC3300), Color(0xFFDD4422), Color(0xFFFF6633)),
    MISTY_MOUNTAIN("Misty Mountain", Color(0xFF4A5555), Color(0xFF6A7777), Color(0xFF8A9999)),
    AUTUMN_LEAVES("Autumn Leaves", Color(0xFF773300), Color(0xFF994422), Color(0xFFCC6633)),

    // New professional themes with superior accessibility
    PROFESSIONAL_BLUE("Professional Blue", Color(0xFF0052CC), Color(0xFF2684FF), Color(0xFF4C9AFF)),
    WARM_EARTH("Warm Earth", Color(0xFF8B4513), Color(0xFFCD853F), Color(0xFFDEB887)),
    COOL_SLATE("Cool Slate", Color(0xFF2F4F4F), Color(0xFF556B2F), Color(0xFF708090)),
    VIBRANT_PURPLE("Vibrant Purple", Color(0xFF6A0DAD), Color(0xFF9932CC), Color(0xFFBA55D3)),
    EMERALD_GREEN("Emerald Green", Color(0xFF046307), Color(0xFF228B22), Color(0xFF32CD32)),
    RUBY_RED("Ruby Red", Color(0xFF8B0000), Color(0xFFDC143C), Color(0xFFFF6347));

    // For backwards compatibility with existing Material3 dynamic theming
    val seedColor: Color get() = primaryColor
}

// UI scale options
enum class UIScaleOption(val displayName: String, val scale: Float) {
    VERY_SMALL("Very Small", 0.85f),
    SMALL("Small", 0.92f),
    MEDIUM("Medium", 1.0f),
    LARGE("Large", 1.15f),
    VERY_LARGE("Very Large", 1.3f)
}

// Font family options
enum class FontFamilyOption(val displayName: String, val fontFamily: FontFamily) {
    DEFAULT("Default", FontFamily.Default),
    SERIF("Serif", FontFamily.Serif),
    SANS_SERIF("Sans Serif", FontFamily.SansSerif),
    MONOSPACE("Monospace", FontFamily.Monospace),
    CURSIVE("Cursive", FontFamily.Cursive)
}

// Dark mode preference options
enum class DarkModePreference(val displayName: String) {
    SYSTEM("Follow System"),
    LIGHT("Always Light"),
    DARK("Always Dark")
}

// Keyboard input mode options
//
// 4-mode design (post v1.267): DICTATION + QWERTY + CODE + EMOJI.
//
// SPECIAL_CHARS / SYSTEM_KEYS / VIBE_CODING / NUMPAD are kept as enum values
// for backward-compatibility with serialized settings. The render layer maps
// them onto CODE via [normalize], so existing user prefs migrate transparently
// without forcing a one-shot DataStore migration.
enum class KeyboardInputMode(val displayName: String) {
    DICTATION("Voice"),
    QWERTY("Text"),
    CODE("Code"),
    EMOJI("Emoji"),

    // Coding-agent quick-command modes. Hidden by default; user enables
    // individual agents in settings. Each renders the same AgentKeyboard
    // composable parameterized by [com.hyperwhisper.data.AgentCommands].
    AGENT_CLAUDE_CODE("Claude Code"),
    AGENT_OPENCODE("OpenCode"),
    AGENT_GEMINI("Gemini CLI"),
    AGENT_CODEX("Codex CLI"),

    // --- Legacy values, transparently rerouted. Kept so old DataStore values
    // still parse. ---
    SPECIAL_CHARS("Symbols"),
    SYSTEM_KEYS("System Keys"),
    VIBE_CODING("Vibe Coding"),
    NUMPAD("Numpad");

    val isAgent: Boolean get() = this in agentModes

    companion object {
        val agentModes: Set<KeyboardInputMode> = setOf(
            AGENT_CLAUDE_CODE, AGENT_OPENCODE, AGENT_GEMINI, AGENT_CODEX
        )
    }

    /**
     * Normalize a stored or in-flight mode to one of the 4 modes the new UI
     * actually exposes. SPECIAL_CHARS folds back into QWERTY (it was the
     * shifted layer); the rest collapse into CODE.
     */
    fun normalize(): KeyboardInputMode = when (this) {
        SPECIAL_CHARS -> QWERTY
        SYSTEM_KEYS, VIBE_CODING, NUMPAD -> CODE
        else -> this
    }
}

// Keyboard layout options (language-specific)
enum class KeyboardLayout(
    val code: String,
    val displayName: String,
    val nativeName: String
) {
    ENGLISH("EN", "English", "English"),
    RUSSIAN("RU", "Russian", "Русский"),
    SPANISH("ES", "Spanish", "Español"),
    FRENCH("FR", "French", "Français"),
    GERMAN("DE", "German", "Deutsch"),
    ARABIC("AR", "Arabic", "العربية")
}

// Appearance settings data class
data class AppearanceSettings(
    val colorScheme: ColorSchemeOption = ColorSchemeOption.OCEAN_DEEP,
    val useDynamicColor: Boolean = true,
    val darkModePreference: DarkModePreference = DarkModePreference.SYSTEM,
    val uiLanguage: String = "en", // UI language code (en, ru, etc.)
    val uiScale: UIScaleOption = UIScaleOption.MEDIUM,
    val fontFamily: FontFamilyOption = FontFamilyOption.DEFAULT,
    val autoCopyToClipboard: Boolean = false,
    val enableHistoryPanel: Boolean = true,
    val techieModeEnabled: Boolean = false, // Show technical details like logs and field info
    /**
     * Set of coding-agent keyboard modes the user has enabled. Empty by
     * default (agent modes hidden). Keys are stored as [KeyboardInputMode]
     * names (string) for forward-compat across enum value changes.
     */
    val enabledAgentKeyboards: Set<String> = emptySet(),
    val showKeyboardSwitcher: Boolean = false, // Show keyboard switcher button on main screen
    val saveOriginalAudioFiles: Boolean = false, // Save audio files for playback/reprocessing from history
    val maxHistoryItems: Int = 20, // Maximum number of history items to keep (0 = unlimited)
    val unlimitedHistory: Boolean = false, // If true, maxHistoryItems is ignored
    val lastKeyboardInputMode: KeyboardInputMode = KeyboardInputMode.DICTATION, // Remember last keyboard mode
    val currentKeyboardLayout: KeyboardLayout = KeyboardLayout.ENGLISH, // Current active layout
    val enabledKeyboardLayouts: Set<KeyboardLayout> = setOf(KeyboardLayout.ENGLISH), // Enabled layouts (EN enabled by default)
    val recentEmojis: List<String> = emptyList(), // Last 10 recently used emojis
    val perAppLayoutMemoryEnabled: Boolean = true // Auto-restore last used layout per foreground app
)

/**
 * Transcription history item
 */
data class TranscriptionHistoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val audioFilePath: String? = null  // Path to saved audio file for reprocessing
)

/**
 * Recording settings
 */
data class RecordingSettings(
    val maxRecordingDuration: Long = 180000L, // 3 minutes in milliseconds
    val warnAtSecondsRemaining: Int = 30
)

/**
 * Usage statistics for a specific model
 */
data class ModelUsage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0
) {
    operator fun plus(other: ModelUsage): ModelUsage {
        return ModelUsage(
            inputTokens = this.inputTokens + other.inputTokens,
            outputTokens = this.outputTokens + other.outputTokens,
            totalTokens = this.totalTokens + other.totalTokens
        )
    }
}

/**
 * Overall usage statistics
 */
data class UsageStatistics(
    val modelUsage: Map<String, ModelUsage> = emptyMap(), // modelId -> usage
    val totalAudioSeconds: Double = 0.0,
    // Final user-visible output characters across all sessions. For two-step
    // (transcription + post-processing) flows, only the post-processing leg
    // counts so we don't double-count the intermediate transcription text.
    val totalCharacters: Long = 0L,
    // UTF-8 byte count of the same final output. Tracked separately from chars
    // because non-Latin scripts run ~2-3 bytes/char.
    val totalBytes: Long = 0L
)

/**
 * Token usage from API response
 */
data class TokenUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int? = null,
    @SerializedName("completion_tokens") val completionTokens: Int? = null,
    @SerializedName("total_tokens") val totalTokens: Int? = null
)

/**
 * Model pricing information (per 1M tokens or per minute)
 */
data class ModelPricing(
    val inputPricePer1M: Double = 0.0,  // Price per 1M input tokens
    val outputPricePer1M: Double = 0.0, // Price per 1M output tokens
    val audioPerMinute: Double = 0.0     // Price per minute of audio (for audio-based models)
)

/**
 * Calculate estimated cost based on usage
 */
fun calculateCost(
    modelId: String,
    inputTokens: Long,
    outputTokens: Long,
    audioSeconds: Double
): Double {
    val pricing = getModelPricing(modelId)

    val tokenCost = (inputTokens / 1_000_000.0) * pricing.inputPricePer1M +
                    (outputTokens / 1_000_000.0) * pricing.outputPricePer1M

    val audioCost = (audioSeconds / 60.0) * pricing.audioPerMinute

    return tokenCost + audioCost
}

/**
 * Get pricing for a specific model
 */
fun getModelPricing(modelId: String): ModelPricing {
    return when {
        // OpenAI hosted transcription models
        modelId == "gpt-4o-transcribe" ->
            ModelPricing(audioPerMinute = 0.006)
        modelId == "gpt-4o-mini-transcribe" ->
            ModelPricing(audioPerMinute = 0.003)

        // Whisper model sizes (self-hosted, free)
        modelId == "base" || modelId == "small" || modelId == "medium" ||
        modelId == "large" || modelId == "large-v2" || modelId == "large-v3" ||
        modelId.contains("whisper-tiny", ignoreCase = true) ||
        modelId.contains("whisper-small", ignoreCase = true) ||
        modelId.contains("whisper-base", ignoreCase = true) ->
            ModelPricing(audioPerMinute = 0.0)

        // OpenAI Whisper - $0.006 per minute (exclude self-hosted and groq models)
        modelId.contains("whisper", ignoreCase = true) &&
            !modelId.contains("groq", ignoreCase = true) &&
            !modelId.contains("tiny", ignoreCase = true) &&
            !modelId.contains("small", ignoreCase = true) &&
            modelId != "base" && modelId != "medium" &&
            modelId != "large" && modelId != "large-v2" && modelId != "large-v3" ->
            ModelPricing(audioPerMinute = 0.006)

        // Groq Whisper - Free tier, very low cost
        modelId.contains("groq", ignoreCase = true) || modelId.contains("distil-whisper", ignoreCase = true) ->
            ModelPricing(audioPerMinute = 0.0)

        // GPT-4 models
        modelId.contains("gpt-4o", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 2.50, outputPricePer1M = 10.0)
        modelId.contains("gpt-4-turbo", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 10.0, outputPricePer1M = 30.0)
        modelId.contains("gpt-4", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 30.0, outputPricePer1M = 60.0)

        // GPT-3.5 models
        modelId.contains("gpt-3.5", ignoreCase = true) || modelId.contains("gpt-35", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 0.50, outputPricePer1M = 1.50)

        // Gemini models
        modelId.contains("gemini-2.0-flash", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 0.10, outputPricePer1M = 0.40, audioPerMinute = 0.006)
        modelId.contains("gemini-1.5-flash", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 0.075, outputPricePer1M = 0.30, audioPerMinute = 0.006)
        modelId.contains("gemini-1.5-pro", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 1.25, outputPricePer1M = 5.0, audioPerMinute = 0.03)

        // Claude models
        modelId.contains("claude-3-opus", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 15.0, outputPricePer1M = 75.0)
        modelId.contains("claude-3-sonnet", ignoreCase = true) || modelId.contains("claude-3.5-sonnet", ignoreCase = true) || modelId.contains("claude-3-5-sonnet", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 3.0, outputPricePer1M = 15.0)
        modelId.contains("claude-3-haiku", ignoreCase = true) || modelId.contains("claude-3.5-haiku", ignoreCase = true) || modelId.contains("claude-3-5-haiku", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 0.25, outputPricePer1M = 1.25)

        // DeepSeek models
        modelId.contains("deepseek-chat", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 0.14, outputPricePer1M = 0.28)
        modelId.contains("deepseek-reasoner", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 0.55, outputPricePer1M = 2.19)

        // Groq models (very cheap/free tier)
        modelId.contains("llama-3.3-70b", ignoreCase = true) || modelId.contains("llama-3.1", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 0.05, outputPricePer1M = 0.08)
        modelId.contains("mixtral", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 0.24, outputPricePer1M = 0.24)

        // Deepgram - $0.0043 per minute
        modelId.contains("nova", ignoreCase = true) || modelId.contains("deepgram", ignoreCase = true) ->
            ModelPricing(audioPerMinute = 0.0043)

        // AssemblyAI - ~$0.00025 per second = $0.015 per minute
        modelId.contains("assemblyai", ignoreCase = true) || modelId == "best" || modelId == "nano" ->
            ModelPricing(audioPerMinute = 0.015)

        // Default: assume token-based pricing
        else -> ModelPricing(inputPricePer1M = 1.0, outputPricePer1M = 2.0)
    }
}
