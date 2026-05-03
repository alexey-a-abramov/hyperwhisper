package com.hyperwhisper.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hyperwhisper.ui.settings.OpenRouterModelInfo

/**
 * Shared OpenRouter catalog browser. Used by both Transcription (where the
 * audio filter is on by default — only voxtral / whisper variants accept
 * audio input) and Post-processing/LLM (where the audio filter is off — all
 * chat-capable models are valid post-processors).
 *
 * The catalog itself is fetched in [com.hyperwhisper.ui.settings.SettingsViewModel.refreshOpenRouterModels]
 * — the same StateFlow feeds both call-sites.
 */
@Composable
fun OpenRouterDiscoveryPanel(
    models: List<OpenRouterModelInfo>,
    refreshing: Boolean,
    error: String?,
    selectedModelId: String,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    audioFilterDefault: Boolean = true,
    audioFilterLabel: String = "Transcription-capable only",
    descriptionText: String =
        "Auto-fetched from openrouter.ai/api/v1/models. Filter for free " +
            "models and audio-capable ones (whisper / voxtral / models reporting audio input)."
) {
    var freeOnly by remember { mutableStateOf(true) }
    var audioOnly by remember { mutableStateOf(audioFilterDefault) }

    val filtered = remember(models, freeOnly, audioOnly) {
        models.asSequence()
            .filter { !freeOnly || it.isFree }
            .filter { !audioOnly || it.supportsAudio }
            .sortedWith(compareByDescending<OpenRouterModelInfo> { it.isFree }.thenBy { it.id })
            .toList()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Discover models",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                OutlinedButton(onClick = onRefresh, enabled = !refreshing) {
                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh")
                }
            }
            Text(
                descriptionText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = freeOnly, onCheckedChange = { freeOnly = it })
                Text("Free only", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(12.dp))
                Checkbox(checked = audioOnly, onCheckedChange = { audioOnly = it })
                Text(audioFilterLabel, style = MaterialTheme.typography.bodyMedium)
            }

            if (error != null) {
                Text(
                    "Refresh failed: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            when {
                models.isEmpty() && !refreshing -> {
                    Text(
                        "Tap Refresh to fetch the OpenRouter catalog.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                filtered.isEmpty() -> {
                    Text(
                        "No models match the current filter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        "${filtered.size} of ${models.size} model(s) match",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    filtered.take(40).forEach { m ->
                        OpenRouterModelRow(
                            model = m,
                            isSelected = m.id == selectedModelId,
                            onSelect = { onSelect(m.id) }
                        )
                    }
                    if (filtered.size > 40) {
                        Text(
                            "+${filtered.size - 40} more — tighten the filter to narrow results.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenRouterModelRow(
    model: OpenRouterModelInfo,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = MaterialTheme.shapes.small,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(model.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    text = buildString {
                        append(model.id)
                        if (model.contextLength > 0) append(" · ${model.contextLength / 1000}k ctx")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (model.isFree) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "FREE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            if (model.supportsAudio) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "AUDIO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
