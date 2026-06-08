package com.hyperwhisper.ui.selectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.LocalModelInfo
import com.hyperwhisper.data.ProviderModelSelection
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.util.formatTestedAgo
import com.hyperwhisper.ui.util.localizedDisplayName

/** One selectable model row: a label, whether it's the active model, and the
 *  action that commits it (a cloud model id, or a local Whisper file path). */
private data class ModelChoice(
    val label: String,
    val isActive: Boolean,
    val onPick: () -> Unit,
)

/**
 * Transcription provider + model picker for the IME.
 *
 * Two-step flow: tap a provider → if it has only one default model, commit
 * immediately; if it has multiple, the row expands to a model list and a
 * second tap commits. This replaces the old dual SELECT / PICK MODEL buttons
 * with a single tap target per row.
 *
 * Ordering: current selection first, then recently-used pairs, then the
 * remaining configured providers. Unconfigured providers don't appear here —
 * that filtering happens in the caller via `configuredProviders`.
 */
@Composable
fun ProviderModelSelectorDialog(
    currentProvider: ApiProvider,
    currentModelId: String,
    configuredProviders: List<ApiProvider>,
    recentSelections: List<ProviderModelSelection>,
    lastTestedAt: Map<ApiProvider, Long> = emptyMap(),
    // Local Whisper is selected by file path (whisperModelPath), not a cloud
    // model id — so its rows come from discovered files and a dedicated setter.
    localWhisperModels: List<LocalModelInfo> = emptyList(),
    currentLocalWhisperPath: String = "",
    onLocalWhisperModelSelected: (String) -> Unit = {},
    onProviderModelSelected: (ApiProvider, String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    var expandedProvider by remember { mutableStateOf<ApiProvider?>(null) }

    val availableProviders = remember(configuredProviders, currentProvider) {
        configuredProviders.distinct().ifEmpty { listOf(currentProvider) }
    }

    // Recency-aware ordering: current → recently-used → others. The first
    // recently-used entry becomes the "default model" we suggest for each
    // provider in the row; falls back to the provider's first defaultModel.
    val orderedProviders = remember(currentProvider, recentSelections, availableProviders) {
        val recentProviders = recentSelections.map { it.provider }.distinct()
        val ordered = mutableListOf<ApiProvider>()
        if (currentProvider in availableProviders) ordered.add(currentProvider)
        ordered.addAll(recentProviders.filter { it in availableProviders && it !in ordered })
        ordered.addAll(availableProviders.filter { it !in ordered })
        ordered
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transcription provider",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${availableProviders.size}/${ApiProvider.entries.size} configured",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(orderedProviders) { provider ->
                    val isCurrent = provider == currentProvider
                    val isExpanded = provider == expandedProvider
                    // Models offered for this provider. Beyond the static
                    // defaults we fold in the user's CURRENT model (when this
                    // is the active provider) and any recently-used models for
                    // it — both possibly custom/discovered ids configured in
                    // Settings. Without this the list is defaults-only, so
                    // tapping here silently downgraded a custom model to a
                    // default (the "I pick it but it reverts" report).
                    val isLocal = provider == ApiProvider.LOCAL_WHISPER
                    val providerModels = remember(provider, isCurrent, currentModelId, recentSelections) {
                        (provider.defaultModels +
                            recentSelections.filter { it.provider == provider }.map { it.modelId } +
                            listOfNotNull(currentModelId.takeIf { isCurrent && it.isNotBlank() }))
                            .filter { it.isNotBlank() }
                            .distinct()
                    }
                    // Selectable rows. Local Whisper is path-based — its rows
                    // come from discovered files and set whisperModelPath;
                    // every other provider uses its model-id list.
                    val choices: List<ModelChoice> = if (isLocal) {
                        localWhisperModels.map { info ->
                            ModelChoice(info.name, info.path == currentLocalWhisperPath) {
                                onLocalWhisperModelSelected(info.path)
                            }
                        }
                    } else {
                        providerModels.map { model ->
                            ModelChoice(model, isCurrent && model == currentModelId) {
                                onProviderModelSelected(provider, model)
                            }
                        }
                    }
                    // Collapsed-row subtitle: for local, the active file name;
                    // otherwise the current/recent/first model id.
                    val suggestedModel = if (isLocal) {
                        (if (isCurrent) currentLocalWhisperPath.substringAfterLast('/') else "")
                            .ifBlank { choices.firstOrNull()?.label.orEmpty() }
                    } else if (isCurrent) {
                        currentModelId
                    } else {
                        recentSelections.firstOrNull { it.provider == provider }?.modelId
                            ?: providerModels.firstOrNull().orEmpty()
                    }
                    val hasMultiple = choices.size > 1
                    Surface(
                        onClick = {
                            if (hasMultiple) {
                                expandedProvider = if (isExpanded) null else provider
                            } else if (isLocal) {
                                // Single discovered file commits on tap.
                                choices.firstOrNull()?.onPick?.invoke()
                            } else {
                                // Single-model providers commit on tap — no
                                // point making the user open a list of one.
                                onProviderModelSelected(provider, suggestedModel)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            isExpanded -> MaterialTheme.colorScheme.surfaceVariant
                            isCurrent -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = provider.localizedDisplayName(),
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isCurrent)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = suggestedModel.ifBlank { "—" },
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isCurrent)
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TestedBadge(
                                    label = formatTestedAgo(lastTestedAt[provider]),
                                    isCurrent = isCurrent,
                                )
                                if (hasMultiple) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Inline model list — appears below the provider
                            // row when the user expands it. Tap a model to
                            // commit and dismiss.
                            if (isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 8.dp, bottom = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    choices.forEach { choice ->
                                        val isCurrentModel = choice.isActive
                                        Surface(
                                            onClick = { choice.onPick() },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isCurrentModel)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                Color.Transparent
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (isCurrentModel) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(Modifier.size(6.dp))
                                                }
                                                Text(
                                                    text = choice.label,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (isCurrentModel)
                                                        MaterialTheme.colorScheme.onPrimary
                                                    else
                                                        MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(strings.cancel.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Compact "✓ Nm/h/d" / "stale" pill for the per-provider tested badge. */
@Composable
private fun TestedBadge(label: String?, isCurrent: Boolean) {
    if (label == null) return
    val isStale = label == "stale"
    val containerColor = when {
        isStale -> MaterialTheme.colorScheme.errorContainer
        isCurrent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = when {
        isStale -> MaterialTheme.colorScheme.onErrorContainer
        isCurrent -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isStale) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(Modifier.size(2.dp))
            }
            Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
