package com.hyperwhisper.ui.settings.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    var showTechnicalDetails by remember { mutableStateOf(false) }

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

        // One toggle gates every "exact filename / install path / repo URL"
        // line across both download cards and the on-disk discovery list.
        // Off by default — friendly names cover the 95% case; the technical
        // strings only matter when the user is debugging or hand-managing
        // files via Termux.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Show technical details",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Filenames, install paths, and other technical metadata",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = showTechnicalDetails,
                onCheckedChange = { showTechnicalDetails = it },
            )
        }

        WhisperDownloadCard(
            states = whisperStates,
            activePath = activeWhisperPath,
            useLocal = useLocalWhisper,
            showTechnicalDetails = showTechnicalDetails,
            onDownload = onStartWhisperDownload,
            onCancel = onCancelWhisperDownload,
            onDelete = onDeleteWhisperDownload,
            onSetActive = onSetActiveWhisper
        )

        GemmaDownloadCard(
            states = gemmaStates,
            activePath = activeGemmaPath,
            useLocal = useLocalGemma,
            showTechnicalDetails = showTechnicalDetails,
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

// region Status badges
//
// Per-row state is communicated via a small badge in the title line —
// rather than tinting the whole row — so the screen stays calm even when
// several models are installed. Four states surface:
//
//   ACTIVE       — model is the configured selection AND on-device toggle
//                  is on. Saturated green: this is what's actually running.
//   SELECTED     — model is the configured selection BUT on-device toggle
//                  is off. Amber nudge: "you picked this; flip the toggle
//                  in Transcription/Post-processing settings to use it."
//   INSTALLED    — file is on disk, neither active nor selected.
//   DOWNLOADING  — download in progress (Downloading / Queued / Retrying).
private val ActiveBadgeColor = Color(0xFF2E7D32)        // material green 800
private val ActiveBadgeOnColor = Color(0xFFFFFFFF)
private val SelectedBadgeColor = Color(0xFFEF6C00)      // material orange 800
private val SelectedBadgeOnColor = Color(0xFFFFFFFF)

private enum class RowStatus { ACTIVE, SELECTED, INSTALLED, DOWNLOADING, NONE }

@Composable
private fun StatusBadge(status: RowStatus) {
    val (label, container, content) = when (status) {
        RowStatus.ACTIVE -> Triple("ACTIVE", ActiveBadgeColor, ActiveBadgeOnColor)
        RowStatus.SELECTED -> Triple("SELECTED", SelectedBadgeColor, SelectedBadgeOnColor)
        RowStatus.INSTALLED -> Triple(
            "INSTALLED",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        RowStatus.DOWNLOADING -> Triple(
            "DOWNLOADING",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        RowStatus.NONE -> return
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun whisperStatus(
    state: WhisperDownloadState,
    isActive: Boolean,
    isSelected: Boolean,
): RowStatus = when {
    isActive -> RowStatus.ACTIVE
    isSelected && (state is WhisperDownloadState.Completed ||
        state is WhisperDownloadState.Paused) -> RowStatus.SELECTED
    state is WhisperDownloadState.Downloading ||
        state is WhisperDownloadState.Queued ||
        state is WhisperDownloadState.Retrying -> RowStatus.DOWNLOADING
    state is WhisperDownloadState.Completed ||
        state is WhisperDownloadState.Paused -> RowStatus.INSTALLED
    else -> RowStatus.NONE
}

private fun gemmaStatus(
    state: GemmaDownloadState,
    isActive: Boolean,
    isSelected: Boolean,
): RowStatus = when {
    isActive -> RowStatus.ACTIVE
    isSelected && (state is GemmaDownloadState.Completed ||
        state is GemmaDownloadState.Paused) -> RowStatus.SELECTED
    state is GemmaDownloadState.Downloading ||
        state is GemmaDownloadState.Queued ||
        state is GemmaDownloadState.Retrying -> RowStatus.DOWNLOADING
    state is GemmaDownloadState.Completed ||
        state is GemmaDownloadState.Paused -> RowStatus.INSTALLED
    else -> RowStatus.NONE
}

// endregion

// region Whisper

@Composable
private fun WhisperDownloadCard(
    states: Map<String, WhisperDownloadState>,
    activePath: String,
    useLocal: Boolean,
    showTechnicalDetails: Boolean,
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
                val pathMatches = activePath.endsWith("/${entry.fileName}")
                WhisperRow(
                    entry = entry,
                    state = states[entry.id] ?: WhisperDownloadState.Idle,
                    isActive = useLocal && pathMatches,
                    isSelected = !useLocal && pathMatches,
                    showTechnicalDetails = showTechnicalDetails,
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
    isSelected: Boolean,
    showTechnicalDetails: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSetActive: (String) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val completedPath = (state as? WhisperDownloadState.Completed)?.path

    // Whole-row primary action: tap-to-set-active when installed-and-not-active,
    // tap-to-download when idle. Active rows + in-flight downloads + failed
    // states are inert at the row level; their actions live in the header.
    val rowOnClick: (() -> Unit)? = when {
        state is WhisperDownloadState.Completed && !isActive ->
            completedPath?.let { p -> { onSetActive(p) } }
        state is WhisperDownloadState.Idle || state is WhisperDownloadState.Cancelled ->
            ({ onDownload() })
        else -> null
    }

    ModelRowSurface(onClick = rowOnClick) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ModelHeader(
                title = entry.displayName,
                subtitle = buildString {
                    append(formatBytes(entry.sizeBytes))
                    append(" · ")
                    append(if (entry.multilingual) "Multilingual" else "English-only")
                    entry.notes?.let { append(" · "); append(it) }
                },
                technicalLine = entry.fileName.takeIf { showTechnicalDetails },
                status = whisperStatus(state, isActive, isSelected),
                actions = {
                    WhisperRowActions(
                        state = state,
                        onDownload = onDownload,
                        onCancel = onCancel,
                        onRequestDelete = { confirmDelete = true },
                    )
                }
            )
            ProgressBlock(state, entry.sizeBytes, showTechnicalDetails)
        }
    }

    DeleteConfirmDialog(
        visible = confirmDelete,
        title = "Delete model?",
        message = "Remove ${entry.displayName} (${formatBytes(entry.sizeBytes)}) from disk. " +
            "You can re-download it later.",
        onConfirm = onDelete,
        onDismiss = { confirmDelete = false },
    )
}

/**
 * Header-cluster icon buttons for a Whisper row. Mapping per state:
 *   Idle / Cancelled         → Download
 *   Downloading / Queued / Retrying → Cancel
 *   Completed                → Delete (confirmed)
 *   Paused                   → Resume + Delete (confirmed)
 *   Failed                   → Retry/Resume + Delete (confirmed) when resumable
 */
@Composable
private fun WhisperRowActions(
    state: WhisperDownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    when (state) {
        is WhisperDownloadState.Idle,
        is WhisperDownloadState.Cancelled ->
            RowActionIcon(Icons.Default.Download, "Download", onDownload)

        is WhisperDownloadState.Downloading,
        is WhisperDownloadState.Queued,
        is WhisperDownloadState.Retrying ->
            RowActionIcon(Icons.Default.Close, "Cancel download", onCancel)

        is WhisperDownloadState.Completed ->
            RowActionIcon(
                Icons.Default.Delete, "Delete", onRequestDelete,
                tint = MaterialTheme.colorScheme.error,
            )

        is WhisperDownloadState.Paused -> {
            RowActionIcon(Icons.Default.PlayArrow, "Resume download", onDownload)
            RowActionIcon(
                Icons.Default.Delete, "Discard partial download", onRequestDelete,
                tint = MaterialTheme.colorScheme.error,
            )
        }

        is WhisperDownloadState.Failed -> {
            RowActionIcon(
                Icons.Default.Refresh,
                if (state.resumableBytes > 0) "Resume download" else "Retry download",
                onDownload,
            )
            if (state.resumableBytes > 0) {
                RowActionIcon(
                    Icons.Default.Delete, "Discard partial download", onRequestDelete,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ProgressBlock(
    state: WhisperDownloadState,
    totalSize: Long,
    showTechnicalDetails: Boolean,
) {
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
        is WhisperDownloadState.Completed -> {
            // Installed-path line is verbose and identical for every model
            // in the catalog; surface it only when the user opts into the
            // technical details switch.
            if (showTechnicalDetails) {
                Text(
                    "Installed at ${state.path.substringBeforeLast('/')}/",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
    showTechnicalDetails: Boolean,
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
                val pathMatches = activePath.endsWith("/${entry.fileName}")
                GemmaRow(
                    entry = entry,
                    state = states[entry.id] ?: GemmaDownloadState.Idle,
                    isActive = useLocal && pathMatches,
                    isSelected = !useLocal && pathMatches,
                    showTechnicalDetails = showTechnicalDetails,
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
                showTechnicalDetails = showTechnicalDetails,
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
    showTechnicalDetails: Boolean,
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
            val isActive = useLocal && activePath == info.path
            val isSelected = !useLocal && activePath == info.path
            DetectedRow(
                info = info,
                isActive = isActive,
                isSelected = isSelected,
                showTechnicalDetails = showTechnicalDetails,
                statusLine = "MediaPipe-loadable · ${formatBytes(info.sizeBytes)}",
                primaryActionLabel = if (isActive) null else "Set active",
                onPrimary = { onSetActive(info.path) },
                onDelete = { onDeleteFile(info.path) }
            )
        }
        incompatible.forEach { info ->
            DetectedRow(
                info = info,
                isActive = false,
                isSelected = false,
                showTechnicalDetails = showTechnicalDetails,
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
    isSelected: Boolean,
    showTechnicalDetails: Boolean,
    statusLine: String,
    statusIsError: Boolean = false,
    primaryActionLabel: String?,
    onPrimary: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    ModelRowSurface {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ModelHeader(
                title = info.name,
                subtitle = statusLine,
                technicalLine = info.path.takeIf { showTechnicalDetails || statusIsError },
                status = when {
                    isActive -> RowStatus.ACTIVE
                    isSelected -> RowStatus.SELECTED
                    else -> RowStatus.INSTALLED
                },
            )
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
    isSelected: Boolean,
    showTechnicalDetails: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSetActive: (String) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val completedPath = (state as? GemmaDownloadState.Completed)?.path

    val rowOnClick: (() -> Unit)? = when {
        state is GemmaDownloadState.Completed && !isActive ->
            completedPath?.let { p -> { onSetActive(p) } }
        state is GemmaDownloadState.Idle || state is GemmaDownloadState.Cancelled ->
            ({ onDownload() })
        else -> null
    }

    ModelRowSurface(onClick = rowOnClick) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ModelHeader(
                title = entry.displayName,
                subtitle = buildString {
                    append(formatBytes(entry.sizeBytes))
                    entry.notes?.let { append(" · "); append(it) }
                },
                technicalLine = entry.fileName.takeIf { showTechnicalDetails },
                status = gemmaStatus(state, isActive, isSelected),
                actions = {
                    GemmaRowActions(
                        state = state,
                        onDownload = onDownload,
                        onCancel = onCancel,
                        onRequestDelete = { confirmDelete = true },
                    )
                }
            )
            GemmaProgressBlock(state, entry.sizeBytes, showTechnicalDetails)
        }
    }

    DeleteConfirmDialog(
        visible = confirmDelete,
        title = "Delete model?",
        message = "Remove ${entry.displayName} (${formatBytes(entry.sizeBytes)}) from disk. " +
            "You can re-download it later.",
        onConfirm = onDelete,
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun GemmaRowActions(
    state: GemmaDownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    when (state) {
        is GemmaDownloadState.Idle,
        is GemmaDownloadState.Cancelled ->
            RowActionIcon(Icons.Default.Download, "Download", onDownload)

        is GemmaDownloadState.Downloading,
        is GemmaDownloadState.Queued,
        is GemmaDownloadState.Retrying ->
            RowActionIcon(Icons.Default.Close, "Cancel download", onCancel)

        is GemmaDownloadState.Completed ->
            RowActionIcon(
                Icons.Default.Delete, "Delete", onRequestDelete,
                tint = MaterialTheme.colorScheme.error,
            )

        is GemmaDownloadState.Paused -> {
            RowActionIcon(Icons.Default.PlayArrow, "Resume download", onDownload)
            RowActionIcon(
                Icons.Default.Delete, "Discard partial download", onRequestDelete,
                tint = MaterialTheme.colorScheme.error,
            )
        }

        is GemmaDownloadState.Failed -> {
            RowActionIcon(
                Icons.Default.Refresh,
                if (state.resumableBytes > 0) "Resume download" else "Retry download",
                onDownload,
            )
            if (state.resumableBytes > 0) {
                RowActionIcon(
                    Icons.Default.Delete, "Discard partial download", onRequestDelete,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun GemmaProgressBlock(
    state: GemmaDownloadState,
    totalSize: Long,
    showTechnicalDetails: Boolean,
) {
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
        is GemmaDownloadState.Completed -> {
            if (showTechnicalDetails) {
                Text(
                    "Installed at ${state.path.substringBeforeLast('/')}/",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {}
    }
}


// endregion

// region Shared helpers

/**
 * Container surface for a model row. Neutral background regardless of
 * status — state is communicated via [StatusBadge] in the row header
 * rather than by tinting the whole row, which kept the screen too busy
 * when several models were installed at once.
 */
@Composable
private fun ModelRowSurface(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    // Two flavors: clickable (Surface(onClick=) gives ripple + accessible
    // role=Button) and inert. Splitting via if instead of always-onClick
    // because Surface with a no-op onClick still grabs focus and shows a
    // ripple, which would suggest tappability that isn't there.
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            content()
        }
    } else {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            content()
        }
    }
}

@Composable
private fun ModelHeader(
    title: String,
    subtitle: String,
    technicalLine: String?,
    status: RowStatus,
    actions: @Composable () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (status != RowStatus.NONE) {
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(status)
                }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (technicalLine != null) {
                Text(
                    technicalLine,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        // Compact icon-button cluster on the right. Each child is a 32dp
        // IconButton — fits two side-by-side without forcing the title to
        // wrap on phone widths. Buttons use their own click target so taps
        // here don't bubble up to the row's Surface(onClick=) handler.
        Row(verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

/**
 * 32dp circular icon-button used in the row header for primary/secondary
 * actions (download, cancel, delete, retry, resume). Smaller than the
 * Material default 48dp so two icons fit comfortably alongside the title
 * without crowding the status badge.
 */
@Composable
private fun RowActionIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Confirmation dialog shared by Whisper and Gemma row delete actions.
 * Returns the [confirmDelete] state so callers can also drive the dialog
 * from any of their action paths (header trash, "Discard" on a paused
 * download, etc.) without each duplicating the dialog body.
 */
@Composable
private fun DeleteConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) {
                Text(
                    "Delete",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
