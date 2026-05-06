package com.hyperwhisper.data

/**
 * One downloadable Gemma variant in the [GemmaModelCatalog].
 *
 * MediaPipe LLM Inference (the engine wired up in
 * [com.hyperwhisper.ime.llm.GemmaInferenceEngine]) loads MediaPipe-converted
 * `.task` and `.litertlm` packages — *not* standard llama.cpp GGUF. Every
 * entry here points at a litert-community artifact that's confirmed
 * compatible with `tasks-genai 0.10.27`.
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
 * Only `.task` / `.litertlm` packages — never `.gguf` — see [GemmaModelEntry]
 * kdoc. Filenames and sizes verified against the HF tree listing 2026-05-06.
 *
 * Curation policy: keep the list short. Headline = Gemma 4 (E2B / E4B). One
 * Gemma 3 1B kept around for the small-fast-test slot. Gemma 2 retired
 * (superseded by Gemma 3 / 4 — no reason to download it new).
 */
object GemmaModelCatalog {
    val ALL: List<GemmaModelEntry> = listOf(
        // ─── Gemma 4 E2B (~2.6–3.3 GB) ──────────────────────────────────
        // Edge-2B variant tuned for phones. CPU `.litertlm` is the default;
        // GPU variants are SoC-specific and only load on matching hardware.
        GemmaModelEntry(
            id = "gemma4-e2b-it-cpu",
            displayName = "Gemma 4 E2B — CPU",
            sizeBytes = 2_590_000_000L,
            repo = "litert-community/gemma-4-E2B-it-litert-lm",
            fileName = "gemma-4-E2B-it.litertlm",
            notes = "CPU · ~2.6 GB · runs on any phone"
        ),
        GemmaModelEntry(
            id = "gemma4-e2b-it-gpu-sm8750",
            displayName = "Gemma 4 E2B — GPU (Qualcomm sm8750)",
            sizeBytes = 3_020_000_000L,
            repo = "litert-community/gemma-4-E2B-it-litert-lm",
            fileName = "gemma-4-E2B-it_qualcomm_sm8750.litertlm",
            notes = "GPU · Qualcomm sm8750 only (won't load on other SoCs) · ~3.0 GB"
        ),

        // ─── Gemma 4 E4B (~3.7 GB) ──────────────────────────────────────
        // Edge-4B for higher-end devices with the RAM headroom; only CPU
        // build is currently published in the litert-lm repo.
        GemmaModelEntry(
            id = "gemma4-e4b-it-cpu",
            displayName = "Gemma 4 E4B — CPU",
            sizeBytes = 3_660_000_000L,
            repo = "litert-community/gemma-4-E4B-it-litert-lm",
            fileName = "gemma-4-E4B-it.litertlm",
            notes = "CPU · ~3.7 GB · best quality, needs ~6 GB RAM free"
        ),

        // ─── Gemma 3 1B — single test entry ─────────────────────────────
        // Kept as the "small fast" slot for exercising the post-processing
        // path without burning 3 GB of bandwidth. q8 / 2K-context picked
        // over the int4 builds because reasoning quality at this scale is
        // already marginal — q4 makes it worse for ~half the size.
        GemmaModelEntry(
            id = "gemma3-1b-it-q8",
            displayName = "Gemma 3 1B — q8 (test/small)",
            sizeBytes = 1_070_000_000L,
            repo = "litert-community/Gemma3-1B-IT",
            fileName = "Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task",
            notes = "CPU · ~1.1 GB · 2K context · for smoke-testing"
        )
    )

    fun byId(id: String): GemmaModelEntry? = ALL.firstOrNull { it.id == id }

    /** All known catalog filenames — used by UI to dedupe against
     *  on-disk discovery so we don't list the same file twice. */
    val knownFileNames: Set<String> = ALL.map { it.fileName }.toSet()
}
