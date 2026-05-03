package com.hyperwhisper.data

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
