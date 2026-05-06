package com.hyperwhisper.ui.settings.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.GemmaDownloadState
import com.hyperwhisper.data.GemmaModelCatalog
import com.hyperwhisper.data.GemmaModelEntry
import com.hyperwhisper.data.LocalModelInfo
import com.hyperwhisper.data.LocalModelType
import com.hyperwhisper.data.WhisperDownloadState
import com.hyperwhisper.data.WhisperModelCatalog
import com.hyperwhisper.data.WhisperModelEntry
import com.hyperwhisper.ui.util.localizedDisplayName

/**
 * Top-level "Local models" settings tab. Hosts both Whisper (transcription)
 * and Gemma (post-processing) downloaders side-by-side. Replaces the old
 * Tools tab inside the Transcription section — by lifting downloads out of
 * provider-config screens, the Cloud/Local tabs there can stay focused on
 * their actual job (configure provider) instead of mixing in maintenance.
 */
@Composable
fun LocalModelsSection(
    whisperStates: Map<String, WhisperDownloadState>,
    gemmaStates: Map<String, GemmaDownloadState>,
    activeWhisperPath: String,
    activeGemmaPath: String,
    useLocalWhisper: Boolean,
    useLocalGemma: Boolean,
    onStartWhisperDownload: (String) -> Unit,
    onCancelWhisperDownload: (String) -> Unit,
    onDeleteWhisperDownload: (String) -> Unit,
    onSetActiveWhisper: (String) -> Unit,
    onStartGemmaDownload: (String) -> Unit,
    onCancelGemmaDownload: (String) -> Unit,
    onDeleteGemmaDownload: (String) -> Unit,
    onSetActiveGemma: (String) -> Unit,
    detectedGemmaFiles: List<LocalModelInfo> = emptyList(),
    onDeleteOnDiskFile: (String) -> Unit = {},
    onRescanOnDisk: () -> Unit = {},
    integrationResults: List<com.hyperwhisper.ui.about.ProviderIntegrationResult> = emptyList(),
    integrationRunning: Boolean = false,
    onRunIntegrationTests: () -> Unit = {},
    onOpenProviderConfiguration: (com.hyperwhisper.data.ApiProvider) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Download speech-recognition (Whisper) and post-processing (Gemma) " +
                "models for fully on-device transcription. Files survive IME teardown " +
                "and app backgrounding via the foreground download service.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        WhisperDownloadCard(
            states = whisperStates,
            activePath = activeWhisperPath,
            useLocal = useLocalWhisper,
            onDownload = onStartWhisperDownload,
            onCancel = onCancelWhisperDownload,
            onDelete = onDeleteWhisperDownload,
            onSetActive = onSetActiveWhisper
        )

        GemmaDownloadCard(
            states = gemmaStates,
            activePath = activeGemmaPath,
            useLocal = useLocalGemma,
            onDownload = onStartGemmaDownload,
            onCancel = onCancelGemmaDownload,
            onDelete = onDeleteGemmaDownload,
            onSetActive = onSetActiveGemma,
            detectedFiles = detectedGemmaFiles,
            onDeleteOnDiskFile = onDeleteOnDiskFile,
            onRescan = onRescanOnDisk
        )

        IntegrationTestCard(
            running = integrationRunning,
            results = integrationResults,
            onRun = onRunIntegrationTests,
            onOpenProviderConfiguration = onOpenProviderConfiguration
        )
    }
}

// region Integration tests

@Composable
private fun IntegrationTestCard(
    running: Boolean,
    results: List<com.hyperwhisper.ui.about.ProviderIntegrationResult>,
    onRun: () -> Unit,
    onOpenProviderConfiguration: (com.hyperwhisper.data.ApiProvider) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val configured = results.filter { it.configured }
    val notConfigured = results.filter { !it.configured }
    val passCount = configured.count { it.success }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Provider integration tests",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (results.isNotEmpty()) {
                    Text(
                        text = "$passCount/${configured.size} pass · ${notConfigured.size} skipped",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                "Probes every cloud provider with a synthetic request to verify " +
                    "endpoint + key validity. Skips providers without an API key set.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onRun,
                    enabled = !running,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (running) "Running…" else "Run all providers", fontSize = 12.sp)
                }
                if (results.isNotEmpty()) {
                    OutlinedButton(onClick = {
                        val json = buildIntegrationResultsJson(results)
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(json))
                        android.widget.Toast.makeText(
                            context,
                            "Copied ${results.size} results as JSON",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }) { Text("Copy JSON", fontSize = 12.sp) }
                }
            }

            if (configured.isNotEmpty()) {
                Text(
                    "CONFIGURED · ${configured.size}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                configured.forEach { IntegrationRow(it, onOpenProviderConfiguration) }
            }
            if (notConfigured.isNotEmpty()) {
                Text(
                    "NOT CONFIGURED · ${notConfigured.size}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                notConfigured.forEach { IntegrationRow(it, onOpenProviderConfiguration) }
            }
        }
    }
}

@Composable
private fun IntegrationRow(
    result: com.hyperwhisper.ui.about.ProviderIntegrationResult,
    onOpenProviderConfiguration: (com.hyperwhisper.data.ApiProvider) -> Unit
) {
    val (glyph, color) = when {
        !result.configured -> "–" to MaterialTheme.colorScheme.onSurfaceVariant
        result.success -> "✓" to MaterialTheme.colorScheme.primary
        else -> "✗" to MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            glyph,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(end = 6.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(result.provider.localizedDisplayName(), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            val detail = buildString {
                result.statusCode?.let { append("HTTP ").append(it).append(" · ") }
                if (result.configured) append(result.durationMs).append(" ms · ")
                append(result.message)
            }
            Text(
                detail,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        androidx.compose.material3.TextButton(
            onClick = { onOpenProviderConfiguration(result.provider) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 6.dp, vertical = 0.dp
            )
        ) { Text("Configure", fontSize = 10.sp, fontWeight = FontWeight.Medium) }
    }
}

private fun buildIntegrationResultsJson(
    results: List<com.hyperwhisper.ui.about.ProviderIntegrationResult>
): String {
    fun esc(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    val items = results.joinToString(",\n") { r ->
        buildString {
            append("  {")
            append("\"provider\":\"").append(esc(r.provider.name)).append("\",")
            append("\"displayName\":\"").append(esc(r.provider.displayName)).append("\",")
            append("\"configured\":").append(r.configured).append(',')
            append("\"success\":").append(r.success).append(',')
            append("\"durationMs\":").append(r.durationMs).append(',')
            append("\"statusCode\":").append(r.statusCode?.toString() ?: "null").append(',')
            append("\"message\":\"").append(esc(r.message)).append("\"")
            append("}")
        }
    }
    return "[\n$items\n]\n"
}

// endregion

// region Whisper

@Composable
private fun WhisperDownloadCard(
    states: Map<String, WhisperDownloadState>,
    activePath: String,
    useLocal: Boolean,
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSetActive: (String) -> Unit
) {
    var multilingualOnly by remember { mutableStateOf(false) }
    var englishOnly by remember { mutableStateOf(false) }
    val entries = remember(multilingualOnly, englishOnly) {
        WhisperModelCatalog.ALL.filter {
            (!multilingualOnly || it.multilingual) && (!englishOnly || !it.multilingual)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Whisper (transcription)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "ggml models from huggingface.co/ggerganov/whisper.cpp · saved to /sdcard/LLM/Whisper/",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChipToggle("Multilingual only", multilingualOnly) {
                    multilingualOnly = it; if (it) englishOnly = false
                }
                ChipToggle("English only", englishOnly) {
                    englishOnly = it; if (it) multilingualOnly = false
                }
            }
            entries.forEach { entry ->
                WhisperRow(
                    entry = entry,
                    state = states[entry.id] ?: WhisperDownloadState.Idle,
                    isActive = useLocal && activePath.endsWith("/${entry.fileName}"),
                    onDownload = { onDownload(entry.id) },
                    onCancel = { onCancel(entry.id) },
                    onDelete = { onDelete(entry.id) },
                    onSetActive = onSetActive
                )
            }
        }
    }
}

@Composable
private fun WhisperRow(
    entry: WhisperModelEntry,
    state: WhisperDownloadState,
    isActive: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSetActive: (String) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ModelHeader(
                title = entry.displayName,
                subtitle = buildString {
                    append(formatBytes(entry.sizeBytes))
                    append(" · ")
                    append(if (entry.multilingual) "Multilingual" else "English-only")
                    entry.notes?.let { append(" · "); append(it) }
                },
                isActive = isActive
            )
            ProgressBlock(state, entry.sizeBytes)
            ActionRow(
                state = state,
                isActive = isActive,
                completedPath = (state as? WhisperDownloadState.Completed)?.path,
                resumableBytes = when (state) {
                    is WhisperDownloadState.Failed -> state.resumableBytes
                    is WhisperDownloadState.Paused -> state.resumableBytes
                    else -> 0L
                },
                onDownload = onDownload, onCancel = onCancel, onDelete = onDelete,
                onSetActive = onSetActive
            )
        }
    }
}

@Composable
private fun ProgressBlock(state: WhisperDownloadState, totalSize: Long) {
    when (state) {
        is WhisperDownloadState.Downloading -> {
            val frac = if (state.totalBytes > 0)
                (state.downloadedBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = frac, modifier = Modifier.fillMaxWidth())
            Text(
                buildString {
                    append("${(frac * 100).toInt()}%  ")
                    append(formatBytes(state.downloadedBytes))
                    append(" / ").append(formatBytes(state.totalBytes))
                    if (state.bytesPerSec > 0) append("  ·  ${formatBytes(state.bytesPerSec)}/s")
                    if (state.etaSeconds > 0) append("  ·  ETA ${formatEta(state.etaSeconds)}")
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is WhisperDownloadState.Queued -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        is WhisperDownloadState.Retrying -> {
            val frac = if (totalSize > 0) (state.resumableBytes.toFloat() / totalSize)
                .coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = frac, modifier = Modifier.fillMaxWidth())
            Text(
                "Retrying ${state.attempt}/${state.maxAttempts} in ${state.backoffSeconds}s — ${state.lastError}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is WhisperDownloadState.Paused -> {
            val frac = if (totalSize > 0) (state.resumableBytes.toFloat() / totalSize)
                .coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = frac, modifier = Modifier.fillMaxWidth())
            Text(
                "Paused at ${formatBytes(state.resumableBytes)} — Resume to continue.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is WhisperDownloadState.Failed -> Text(
            "Failed: ${state.message}" +
                if (state.resumableBytes > 0) "  ·  ${formatBytes(state.resumableBytes)} saved — Retry resumes." else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
        is WhisperDownloadState.Completed -> Text(
            "Installed at ${state.path.substringBeforeLast('/')}/",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> {}
    }
}

// endregion

// region Gemma

@Composable
private fun GemmaDownloadCard(
    states: Map<String, GemmaDownloadState>,
    activePath: String,
    useLocal: Boolean,
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSetActive: (String) -> Unit,
    detectedFiles: List<LocalModelInfo>,
    onDeleteOnDiskFile: (String) -> Unit,
    onRescan: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Gemma (post-processing)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "MediaPipe-converted .task / .litertlm files from huggingface.co/litert-community · " +
                    "saved to /sdcard/LLM/Gemma/. Standard llama.cpp GGUF files won't load — use these. " +
                    "All listed repos are gated; accept the Gemma license on HF (logged in) before downloading.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            GemmaModelCatalog.ALL.forEach { entry ->
                GemmaRow(
                    entry = entry,
                    state = states[entry.id] ?: GemmaDownloadState.Idle,
                    isActive = useLocal && activePath.endsWith("/${entry.fileName}"),
                    onDownload = { onDownload(entry.id) },
                    onCancel = { onCancel(entry.id) },
                    onDelete = { onDelete(entry.id) },
                    onSetActive = onSetActive
                )
            }

            DetectedOnDiskBlock(
                detected = detectedFiles,
                activePath = activePath,
                useLocal = useLocal,
                onSetActive = onSetActive,
                onDeleteFile = onDeleteOnDiskFile,
                onRescan = onRescan
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://huggingface.co/litert-community")
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Browse on Hugging Face", fontSize = 12.sp) }
                OutlinedButton(
                    onClick = onRescan,
                    modifier = Modifier.weight(1f)
                ) { Text("Rescan disk", fontSize = 12.sp) }
            }
        }
    }
}

/**
 * Shows files we found on disk that aren't part of the curated catalog. Two
 * buckets: loadable (.task / .litertlm / non-gguf .bin) get a "Set active"
 * action; incompatible (.gguf) get a clear explanation + one-tap delete so
 * the user can free the (often multi-GB) disk space without going through a
 * file manager. The user has 8+ GB of GGUF Gemma 4 files from confusing
 * `gguf` with `litertlm` — surfacing them here is the whole point.
 */
@Composable
private fun DetectedOnDiskBlock(
    detected: List<LocalModelInfo>,
    activePath: String,
    useLocal: Boolean,
    onSetActive: (String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onRescan: () -> Unit
) {
    // Hide files already represented by a catalog row (they get their own UI).
    val orphans = remember(detected) {
        detected.filter {
            it.type == LocalModelType.GEMMA &&
                it.name !in GemmaModelCatalog.knownFileNames
        }
    }
    if (orphans.isEmpty()) return

    val (loadable, incompatible) = orphans.partition { isLoadableByMediaPipe(it.name) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "DETECTED ON DISK · ${orphans.size}",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        loadable.forEach { info ->
            DetectedRow(
                info = info,
                isActive = useLocal && activePath == info.path,
                statusLine = "MediaPipe-loadable · ${formatBytes(info.sizeBytes)}",
                primaryActionLabel = if (useLocal && activePath == info.path) null else "Set active",
                onPrimary = { onSetActive(info.path) },
                onDelete = { onDeleteFile(info.path) }
            )
        }
        incompatible.forEach { info ->
            DetectedRow(
                info = info,
                isActive = false,
                statusLine = "Incompatible (GGUF — needs .litertlm from litert-community) · " +
                    formatBytes(info.sizeBytes),
                statusIsError = true,
                primaryActionLabel = null,
                onPrimary = {},
                onDelete = { onDeleteFile(info.path) }
            )
        }
    }
}

@Composable
private fun DetectedRow(
    info: LocalModelInfo,
    isActive: Boolean,
    statusLine: String,
    statusIsError: Boolean = false,
    primaryActionLabel: String?,
    onPrimary: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ModelHeader(title = info.name, subtitle = statusLine, isActive = isActive)
            if (statusIsError) {
                Text(
                    info.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (primaryActionLabel != null) {
                    Button(onClick = onPrimary, modifier = Modifier.weight(1f)) {
                        Text(primaryActionLabel)
                    }
                }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.weight(1f)
                ) { Text("Delete") }
            }
        }
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete file?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(info.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        info.path,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Frees ${formatBytes(info.sizeBytes)}. This cannot be undone.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text(
                        "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** Mirrors the runtime check in GemmaInferenceEngine — only .gguf is rejected
 *  outright; .task / .litertlm / .bin can all be attempted by tasks-genai. */
private fun isLoadableByMediaPipe(fileName: String): Boolean {
    val lower = fileName.lowercase()
    if (lower.endsWith(".gguf")) return false
    return lower.endsWith(".task") || lower.endsWith(".litertlm") || lower.endsWith(".bin")
}

@Composable
private fun GemmaRow(
    entry: GemmaModelEntry,
    state: GemmaDownloadState,
    isActive: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSetActive: (String) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ModelHeader(
                title = entry.displayName,
                subtitle = buildString {
                    append(formatBytes(entry.sizeBytes))
                    entry.notes?.let { append(" · "); append(it) }
                },
                isActive = isActive
            )
            GemmaProgressBlock(state, entry.sizeBytes)
            GemmaActionRow(
                state = state,
                isActive = isActive,
                completedPath = (state as? GemmaDownloadState.Completed)?.path,
                resumableBytes = when (state) {
                    is GemmaDownloadState.Failed -> state.resumableBytes
                    is GemmaDownloadState.Paused -> state.resumableBytes
                    else -> 0L
                },
                onDownload = onDownload, onCancel = onCancel, onDelete = onDelete,
                onSetActive = onSetActive
            )
        }
    }
}

@Composable
private fun GemmaProgressBlock(state: GemmaDownloadState, totalSize: Long) {
    when (state) {
        is GemmaDownloadState.Downloading -> {
            val frac = if (state.totalBytes > 0)
                (state.downloadedBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = frac, modifier = Modifier.fillMaxWidth())
            Text(
                buildString {
                    append("${(frac * 100).toInt()}%  ")
                    append(formatBytes(state.downloadedBytes))
                    append(" / ").append(formatBytes(state.totalBytes))
                    if (state.bytesPerSec > 0) append("  ·  ${formatBytes(state.bytesPerSec)}/s")
                    if (state.etaSeconds > 0) append("  ·  ETA ${formatEta(state.etaSeconds)}")
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is GemmaDownloadState.Queued -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        is GemmaDownloadState.Retrying -> {
            val frac = if (totalSize > 0)
                (state.resumableBytes.toFloat() / totalSize).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = frac, modifier = Modifier.fillMaxWidth())
            Text(
                "Retrying ${state.attempt}/${state.maxAttempts} in ${state.backoffSeconds}s — ${state.lastError}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is GemmaDownloadState.Paused -> {
            val frac = if (totalSize > 0)
                (state.resumableBytes.toFloat() / totalSize).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = frac, modifier = Modifier.fillMaxWidth())
            Text(
                "Paused at ${formatBytes(state.resumableBytes)} — Resume to continue.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is GemmaDownloadState.Failed -> Text(
            "Failed: ${state.message}" +
                if (state.resumableBytes > 0) "  ·  ${formatBytes(state.resumableBytes)} saved — Retry resumes." else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
        is GemmaDownloadState.Completed -> Text(
            "Installed at ${state.path.substringBeforeLast('/')}/",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> {}
    }
}

@Composable
private fun GemmaActionRow(
    state: GemmaDownloadState,
    isActive: Boolean,
    completedPath: String?,
    resumableBytes: Long,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSetActive: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            is GemmaDownloadState.Downloading,
            is GemmaDownloadState.Queued,
            is GemmaDownloadState.Retrying ->
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }

            is GemmaDownloadState.Completed -> {
                if (!isActive) Button(
                    onClick = { completedPath?.let(onSetActive) },
                    modifier = Modifier.weight(1f)
                ) { Text("Set active") }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Delete") }
            }
            is GemmaDownloadState.Paused -> {
                Button(onClick = onDownload, modifier = Modifier.weight(1f)) { Text("Resume") }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Discard") }
            }
            is GemmaDownloadState.Failed -> {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.weight(1f)
                ) { Text(if (resumableBytes > 0) "Resume" else "Retry") }
                if (resumableBytes > 0) OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                ) { Text("Discard") }
            }
            else -> Button(onClick = onDownload, modifier = Modifier.weight(1f)) { Text("Download") }
        }
    }
}

// endregion

// region Shared helpers

@Composable
private fun ModelHeader(title: String, subtitle: String, isActive: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (isActive) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "Active",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionRow(
    state: WhisperDownloadState,
    isActive: Boolean,
    completedPath: String?,
    resumableBytes: Long,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSetActive: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            is WhisperDownloadState.Downloading,
            is WhisperDownloadState.Queued,
            is WhisperDownloadState.Retrying ->
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }

            is WhisperDownloadState.Completed -> {
                if (!isActive) Button(
                    onClick = { completedPath?.let(onSetActive) },
                    modifier = Modifier.weight(1f)
                ) { Text("Set active") }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Delete") }
            }
            is WhisperDownloadState.Paused -> {
                Button(onClick = onDownload, modifier = Modifier.weight(1f)) { Text("Resume") }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Discard") }
            }
            is WhisperDownloadState.Failed -> {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.weight(1f)
                ) { Text(if (resumableBytes > 0) "Resume" else "Retry") }
                if (resumableBytes > 0) OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                ) { Text("Discard") }
            }
            else -> Button(onClick = onDownload, modifier = Modifier.weight(1f)) { Text("Download") }
        }
    }
}

@Composable
private fun ChipToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onChange(!checked) },
        shape = MaterialTheme.shapes.small,
        color = if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (checked) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private fun formatBytes(b: Long): String {
    if (b <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var v = b.toDouble(); var i = 0
    while (v >= 1024.0 && i < units.lastIndex) { v /= 1024.0; i++ }
    return if (i <= 1) "${v.toInt()} ${units[i]}" else "%.1f %s".format(v, units[i])
}

private fun formatEta(seconds: Int): String {
    if (seconds < 60) return "${seconds}s"
    val m = seconds / 60; val s = seconds % 60
    if (m < 60) return "${m}m ${s}s"
    val h = m / 60; val mm = m % 60
    return "${h}h ${mm}m"
}

// endregion
