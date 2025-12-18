package com.hyperwhisper.data

import com.google.gson.annotations.SerializedName

/**
 * Domain Models
 */
data class Language(
    val code: String, // ISO-639-1 code
    val name: String
)

// Supported languages list - comprehensive for most ASR models
val SUPPORTED_LANGUAGES = listOf(
    Language("", "Auto-detect"),
    // Major languages
    Language("en", "English"),
    Language("zh", "Chinese (Mandarin)"),
    Language("es", "Spanish"),
    Language("hi", "Hindi"),
    Language("ar", "Arabic"),
    Language("bn", "Bengali"),
    Language("pt", "Portuguese"),
    Language("ru", "Russian"),
    Language("ja", "Japanese"),
    Language("pa", "Punjabi"),
    Language("de", "German"),
    Language("jv", "Javanese"),
    Language("ko", "Korean"),
    Language("fr", "French"),
    Language("te", "Telugu"),
    Language("mr", "Marathi"),
    Language("tr", "Turkish"),
    Language("ta", "Tamil"),
    Language("vi", "Vietnamese"),
    Language("ur", "Urdu"),
    Language("it", "Italian"),
    Language("th", "Thai"),
    Language("gu", "Gujarati"),
    Language("fa", "Persian (Farsi)"),
    Language("pl", "Polish"),
    Language("uk", "Ukrainian"),
    Language("kn", "Kannada"),
    Language("ml", "Malayalam"),
    Language("or", "Odia"),
    Language("my", "Burmese"),
    Language("si", "Sinhala"),
    // European languages
    Language("nl", "Dutch"),
    Language("ro", "Romanian"),
    Language("cs", "Czech"),
    Language("sv", "Swedish"),
    Language("da", "Danish"),
    Language("fi", "Finnish"),
    Language("no", "Norwegian"),
    Language("el", "Greek"),
    Language("he", "Hebrew"),
    Language("hu", "Hungarian"),
    Language("bg", "Bulgarian"),
    Language("hr", "Croatian"),
    Language("sr", "Serbian"),
    Language("sk", "Slovak"),
    Language("sl", "Slovenian"),
    Language("lt", "Lithuanian"),
    Language("lv", "Latvian"),
    Language("et", "Estonian"),
    Language("ga", "Irish"),
    Language("is", "Icelandic"),
    Language("mt", "Maltese"),
    Language("cy", "Welsh"),
    // Asian languages
    Language("km", "Khmer"),
    Language("lo", "Lao"),
    Language("ne", "Nepali"),
    Language("am", "Amharic"),
    Language("az", "Azerbaijani"),
    Language("ka", "Georgian"),
    Language("hy", "Armenian"),
    Language("kk", "Kazakh"),
    Language("uz", "Uzbek"),
    Language("tg", "Tajik"),
    Language("tk", "Turkmen"),
    Language("ky", "Kyrgyz"),
    Language("mn", "Mongolian"),
    // African languages
    Language("sw", "Swahili"),
    Language("ha", "Hausa"),
    Language("yo", "Yoruba"),
    Language("ig", "Igbo"),
    Language("zu", "Zulu"),
    Language("xh", "Xhosa"),
    Language("af", "Afrikaans"),
    Language("so", "Somali"),
    // Other
    Language("ms", "Malay"),
    Language("id", "Indonesian"),
    Language("tl", "Tagalog"),
    Language("ceb", "Cebuano"),
    Language("bs", "Bosnian"),
    Language("mk", "Macedonian"),
    Language("sq", "Albanian"),
    Language("be", "Belarusian"),
    Language("eu", "Basque"),
    Language("ca", "Catalan"),
    Language("gl", "Galician")
)

data class VoiceMode(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val isBuiltIn: Boolean = false,
    val inputLanguageHint: String = "" // Hint for input language if model supports it
)

data class ApiSettings(
    val provider: ApiProvider = ApiProvider.OPENAI,
    val baseUrl: String = "",
    val apiKeys: Map<ApiProvider, String> = emptyMap(), // Per-provider API keys
    val modelId: String = "whisper-1",
    val inputLanguage: String = "", // ISO-639-1 code for speech input - empty for auto-detect
    val outputLanguage: String = "" // ISO-639-1 code for output - empty to keep original
) {
    // Helper to get API key for current provider
    fun getCurrentApiKey(): String = apiKeys[provider] ?: ""
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
    @SerializedName("choices") val choices: List<Choice>
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
 * Result wrapper for API calls
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}
