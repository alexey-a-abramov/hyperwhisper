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
    val systemPrompt: String,
    val isBuiltIn: Boolean = false,
    val inputLanguageHint: String = "" // Hint for input language if model supports it
)

/**
 * Settings specific to LOCAL provider
 */
data class LocalSettings(
    val selectedModel: WhisperModel = WhisperModel.BASE,
    val enableSecondStageProcessing: Boolean = false,
    val secondStageProvider: ApiProvider = ApiProvider.OPENAI,
    val secondStageModel: String = "gpt-4o-mini"
)

data class ApiSettings(
    val provider: ApiProvider = ApiProvider.OPENAI,
    val baseUrl: String = "",
    val apiKeys: Map<ApiProvider, String> = emptyMap(), // Per-provider API keys
    val modelId: String = "whisper-1",
    val inputLanguage: String = "", // ISO-639-1 code for speech input - empty for auto-detect
    val outputLanguage: String = "", // ISO-639-1 code for output - empty to keep original
    val localSettings: LocalSettings = LocalSettings()
) {
    // Helper to get API key for current provider
    fun getCurrentApiKey(): String = apiKeys[provider] ?: ""

    // Helper to get API key for second-stage processing
    fun getSecondStageApiKey(): String = apiKeys[localSettings.secondStageProvider] ?: ""
}

enum class ApiProvider(
    val displayName: String,
    val defaultEndpoint: String,
    val defaultModels: List<String>
) {
    OPENAI(
        displayName = "OpenAI Whisper",
        defaultEndpoint = "https://api.openai.com/v1/",
        defaultModels = listOf("whisper-1")
    ),
    DEEPGRAM(
        displayName = "Deepgram",
        defaultEndpoint = "https://api.deepgram.com/v1/",
        defaultModels = listOf("nova-2", "nova-3", "nova", "whisper", "base", "enhanced")
    ),
    ASSEMBLYAI(
        displayName = "AssemblyAI",
        defaultEndpoint = "https://api.assemblyai.com/v2/",
        defaultModels = listOf("best", "nano")
    ),
    GOOGLE_CLOUD(
        displayName = "Google Cloud Speech",
        defaultEndpoint = "https://speech.googleapis.com/v1/",
        defaultModels = listOf("chirp", "long", "phone_call", "video", "command_and_search", "default")
    ),
    AWS_TRANSCRIBE(
        displayName = "AWS Transcribe",
        defaultEndpoint = "https://transcribe.us-east-1.amazonaws.com/",
        defaultModels = listOf("standard", "medical", "call-analytics")
    ),
    AZURE_SPEECH(
        displayName = "Azure AI Speech",
        defaultEndpoint = "https://eastus.stt.speech.microsoft.com/",
        defaultModels = listOf("default", "conversation", "dictation", "interactive")
    ),
    REVAI(
        displayName = "Rev.ai",
        defaultEndpoint = "https://api.rev.ai/speechtotext/v1/",
        defaultModels = listOf("rev", "rev_human_fallback")
    ),
    GROQ(
        displayName = "Groq Whisper",
        defaultEndpoint = "https://api.groq.com/openai/v1/",
        defaultModels = listOf("whisper-large-v3", "whisper-large-v3-turbo", "distil-whisper-large-v3-en")
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultEndpoint = "https://openrouter.ai/api/v1/",
        defaultModels = listOf("whisper-1", "whisper-large-v3")
    ),
    GEMINI(
        displayName = "Google Gemini",
        defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta/",
        defaultModels = listOf("gemini-1.5-flash", "gemini-1.5-pro", "gemini-2.0-flash-exp")
    ),
    HUGGINGFACE(
        displayName = "Hugging Face",
        defaultEndpoint = "https://api-inference.huggingface.co/models/",
        defaultModels = listOf("openai/whisper-large-v3", "openai/whisper-medium", "openai/whisper-small")
    ),
    LOCAL(
        displayName = "Local (On-Device)",
        defaultEndpoint = "",
        defaultModels = listOf("tiny", "base", "small")
    )
}

enum class RecordingState {
    IDLE,
    RECORDING,
    PROCESSING,
    ERROR
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
    @SerializedName("text") val text: String
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
    val postProcessingTokens: TokenUsage? = null // Tokens used for post-processing (if applicable)
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
enum class ColorSchemeOption(
    val displayName: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val tertiaryColor: Color
) {
    TERMINAL_DARK("Terminal Dark", Color(0xFF1E1E1E), Color(0xFF00FF00), Color(0xFF0080FF)),
    OCEAN_DEEP("Ocean Deep", Color(0xFF006994), Color(0xFF00B4D8), Color(0xFF90E0EF)),
    FOREST_NIGHT("Forest Night", Color(0xFF2D5016), Color(0xFF4A7C3B), Color(0xFF6FA05D)),
    SUNSET_HORIZON("Sunset Horizon", Color(0xFFFF6B35), Color(0xFFF7931E), Color(0xFFFDC435)),
    ARCTIC_FROST("Arctic Frost", Color(0xFF4A90A4), Color(0xFF87C9DD), Color(0xFFD4F1F4)),
    DESERT_STORM("Desert Storm", Color(0xFFD4A574), Color(0xFFE8B86D), Color(0xFFF4E5D3)),
    NEON_CITY("Neon City", Color(0xFFFF10F0), Color(0xFF00F0FF), Color(0xFFFFFF00)),
    CHERRY_BLOSSOM("Cherry Blossom", Color(0xFFFFB7C5), Color(0xFFF4A8C1), Color(0xFFE899DC)),
    MIDNIGHT_SKY("Midnight Sky", Color(0xFF191970), Color(0xFF4B0082), Color(0xFF6A5ACD)),
    LAVA_FLOW("Lava Flow", Color(0xFFFF4500), Color(0xFFFF6347), Color(0xFFFF8C00)),
    MISTY_MOUNTAIN("Misty Mountain", Color(0xFF5F6A6A), Color(0xFF85929E), Color(0xFFAEB6BF)),
    AUTUMN_LEAVES("Autumn Leaves", Color(0xFF8B4513), Color(0xFFD2691E), Color(0xFFFFAF4D));

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

// Appearance settings data class
data class AppearanceSettings(
    val colorScheme: ColorSchemeOption = ColorSchemeOption.OCEAN_DEEP,
    val useDynamicColor: Boolean = true,
    val darkModePreference: DarkModePreference = DarkModePreference.SYSTEM,
    val uiLanguage: String = "en", // UI language code (en, ru, etc.)
    val uiScale: UIScaleOption = UIScaleOption.MEDIUM,
    val fontFamily: FontFamilyOption = FontFamilyOption.DEFAULT,
    val autoCopyToClipboard: Boolean = true,
    val enableHistoryPanel: Boolean = true
)

/**
 * Transcription history item
 */
data class TranscriptionHistoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
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
    val totalAudioSeconds: Double = 0.0
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
        // OpenAI Whisper - $0.006 per minute
        modelId.contains("whisper", ignoreCase = true) && !modelId.contains("groq", ignoreCase = true) ->
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
        modelId.contains("claude-3-sonnet", ignoreCase = true) || modelId.contains("claude-3.5-sonnet", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 3.0, outputPricePer1M = 15.0)
        modelId.contains("claude-3-haiku", ignoreCase = true) ->
            ModelPricing(inputPricePer1M = 0.25, outputPricePer1M = 1.25)

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

/**
 * Local Whisper Model Information
 */
enum class WhisperModel(
    val modelName: String,
    val displayName: String,
    val fileSize: Long, // Bytes
    val fileName: String,
    val downloadUrl: String,
    val isRecommended: Boolean = false
) {
    TINY(
        modelName = "tiny",
        displayName = "Tiny (Fast)",
        fileSize = 75L * 1024 * 1024, // ~75 MB
        fileName = "ggml-tiny.bin",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
        isRecommended = true
    ),
    BASE(
        modelName = "base",
        displayName = "Base (Balanced)",
        fileSize = 142L * 1024 * 1024, // ~142 MB
        fileName = "ggml-base.bin",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
        isRecommended = true
    ),
    SMALL(
        modelName = "small",
        displayName = "Small (Accurate)",
        fileSize = 466L * 1024 * 1024, // ~466 MB
        fileName = "ggml-small.bin",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"
    );

    fun getFormattedSize(): String {
        val mb = fileSize / (1024.0 * 1024.0)
        return "%.0f MB".format(mb)
    }
}

/**
 * Model download state
 */
sealed class ModelDownloadState {
    object NotDownloaded : ModelDownloadState()
    data class Downloading(val progress: Float) : ModelDownloadState() // 0.0 - 1.0
    object Downloaded : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}

/**
 * Model info with download state
 */
data class WhisperModelInfo(
    val model: WhisperModel,
    val downloadState: ModelDownloadState,
    val localPath: File? = null
)
