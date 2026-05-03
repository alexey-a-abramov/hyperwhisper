package com.hyperwhisper.data

data class WhisperModelEntry(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val multilingual: Boolean,
    val fileName: String,
    val notes: String? = null
) {
    val downloadUrl: String
        get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$fileName"
}

object WhisperModelCatalog {
    val ALL: List<WhisperModelEntry> = listOf(
        WhisperModelEntry(
            id = "tiny.en",
            displayName = "Tiny — English",
            sizeBytes = 75_000_000L,
            multilingual = false,
            fileName = "ggml-tiny.en.bin",
            notes = "Fastest, low accuracy"
        ),
        WhisperModelEntry(
            id = "tiny",
            displayName = "Tiny — Multilingual",
            sizeBytes = 75_000_000L,
            multilingual = true,
            fileName = "ggml-tiny.bin",
            notes = "Fastest, multilingual"
        ),
        WhisperModelEntry(
            id = "base.en",
            displayName = "Base — English",
            sizeBytes = 142_000_000L,
            multilingual = false,
            fileName = "ggml-base.en.bin",
            notes = "Recommended for English-only"
        ),
        WhisperModelEntry(
            id = "base",
            displayName = "Base — Multilingual",
            sizeBytes = 142_000_000L,
            multilingual = true,
            fileName = "ggml-base.bin",
            notes = "Recommended general-purpose"
        ),
        WhisperModelEntry(
            id = "small.en",
            displayName = "Small — English",
            sizeBytes = 466_000_000L,
            multilingual = false,
            fileName = "ggml-small.en.bin"
        ),
        WhisperModelEntry(
            id = "small",
            displayName = "Small — Multilingual",
            sizeBytes = 466_000_000L,
            multilingual = true,
            fileName = "ggml-small.bin"
        ),
        WhisperModelEntry(
            id = "medium.en",
            displayName = "Medium — English",
            sizeBytes = 1_500_000_000L,
            multilingual = false,
            fileName = "ggml-medium.en.bin",
            notes = "High accuracy, slower"
        ),
        WhisperModelEntry(
            id = "medium",
            displayName = "Medium — Multilingual",
            sizeBytes = 1_500_000_000L,
            multilingual = true,
            fileName = "ggml-medium.bin"
        ),
        WhisperModelEntry(
            id = "large-v3-turbo",
            displayName = "Large v3 Turbo",
            sizeBytes = 1_624_000_000L,
            multilingual = true,
            fileName = "ggml-large-v3-turbo.bin",
            notes = "Best quality-to-speed ratio"
        ),
        WhisperModelEntry(
            id = "large-v3",
            displayName = "Large v3",
            sizeBytes = 3_094_000_000L,
            multilingual = true,
            fileName = "ggml-large-v3.bin",
            notes = "Best quality, slowest"
        )
    )

    fun byId(id: String): WhisperModelEntry? = ALL.firstOrNull { it.id == id }
}
