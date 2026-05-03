package com.hyperwhisper.data

/**
 * One downloadable Gemma variant in the [GemmaModelCatalog].
 *
 * MediaPipe LLM Inference (the engine wired up in
 * [com.hyperwhisper.ime.llm.GemmaInferenceEngine]) loads MediaPipe-converted
 * `.bin` / `.task` packages — *not* standard llama.cpp GGUF. Every entry here
 * points at a litert-community artifact that's confirmed compatible.
 *
 * URL is constructed from [repo] + [fileName]; both must match the upstream
 * Hugging Face layout exactly. Sizes are approximate — the server's
 * `Content-Length` overrides at runtime; the catalog value is just used for
 * the initial progress bar before the first byte arrives.
 *
 * NOTE: litert-community repos are gated (require Gemma-license acceptance on
 * Hugging Face). Downloads will return HTTP 401/403 unless the user has
 * accepted the license on the website with a logged-in account, or the file
 * has been mirrored to a public location. Surfacing this error to the user
 * is the UI's job; the downloader will report it as a HardFail so we don't
 * burn retries.
 */
data class GemmaModelEntry(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val repo: String,
    val fileName: String,
    /** Free-form note shown in the UI, e.g. "GPU (Snapdragon 8 Gen 2)". */
    val notes: String? = null
) {
    val downloadUrl: String
        get() = "https://huggingface.co/$repo/resolve/main/$fileName"
}

/**
 * Curated list of MediaPipe-compatible Gemma builds from
 * [huggingface.co/litert-community](https://huggingface.co/litert-community).
 *
 * Only `.task` / `.bin` packages — never `.gguf` — see [GemmaModelEntry] kdoc.
 * Filename and size verified against the HF tree listing 2026-05-02; if the
 * upstream renames a file, the entry needs updating here.
 */
object GemmaModelCatalog {
    val ALL: List<GemmaModelEntry> = listOf(
        // ─── Gemma 3 1B (~700MB) ─────────────────────────────────────────
        GemmaModelEntry(
            id = "gemma3-1b-it-int4",
            displayName = "Gemma 3 1B — int4 (CPU)",
            sizeBytes = 555_000_000L,
            repo = "litert-community/Gemma3-1B-IT",
            fileName = "gemma3-1b-it-int4.task",
            notes = "CPU · smallest (~555MB) · recommended starter"
        ),
        GemmaModelEntry(
            id = "gemma3-1b-it-int4-web",
            displayName = "Gemma 3 1B — int4 web",
            sizeBytes = 700_000_000L,
            repo = "litert-community/Gemma3-1B-IT",
            fileName = "gemma3-1b-it-int4-web.task",
            notes = "CPU · ~700MB · longer context"
        ),
        GemmaModelEntry(
            id = "gemma3-1b-it-q4-sm8650",
            displayName = "Gemma 3 1B — q4 GPU (Snapdragon 8 Gen 2)",
            sizeBytes = 690_000_000L,
            repo = "litert-community/Gemma3-1B-IT",
            fileName = "Gemma3-1B-IT_q4_ekv1280_sm8650.litertlm",
            notes = "GPU · Snapdragon SM8650 only"
        ),
        GemmaModelEntry(
            id = "gemma3-1b-it-q4-sm8750",
            displayName = "Gemma 3 1B — q4 GPU (Snapdragon 8 Gen 3)",
            sizeBytes = 689_000_000L,
            repo = "litert-community/Gemma3-1B-IT",
            fileName = "Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm",
            notes = "GPU · Snapdragon SM8750 only"
        ),

        // ─── Gemma 2 2B (~1.5–2.7GB) ────────────────────────────────────
        GemmaModelEntry(
            id = "gemma2-2b-it-int8",
            displayName = "Gemma 2 2B — int8",
            sizeBytes = 2_700_000_000L,
            repo = "litert-community/Gemma2-2B-IT",
            fileName = "Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task",
            notes = "CPU · ~2.7GB · highest 2B quality"
        ),

        // ─── Gemma 2 9B (~5GB) ──────────────────────────────────────────
        // The 9B repo is gated and (as of 2026-05) not yet published as a
        // single .task in litert-community. Filename below is the documented
        // pattern; verify via the HF tree before relying on it. Kept as a
        // placeholder so the UI shows the size tier.
        GemmaModelEntry(
            id = "gemma2-9b-it-int4",
            displayName = "Gemma 2 9B — int4",
            sizeBytes = 5_200_000_000L,
            repo = "litert-community/Gemma2-9B-IT",
            fileName = "gemma2-9b-it-int4.task",
            notes = "CPU · ~5GB · slow on phones, tablets only"
        )
    )

    fun byId(id: String): GemmaModelEntry? = ALL.firstOrNull { it.id == id }
}
