package com.hyperwhisper.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.LlmProvider
import com.hyperwhisper.network.ConnectionTester
import com.hyperwhisper.ui.util.localizedDisplayName

/**
 * Status + maintenance cards shared across settings surfaces.
 *
 * These used to be private composables on the Settings home screen. They were
 * lifted out so "Active configuration" can live in About (it's status, not
 * navigation) and "Re-test providers" can live in Advanced (it's a diagnostic),
 * keeping the home screen a clean list of areas to configure.
 */

/** At-a-glance summary of the active transcription + post-processing setup. */
@Composable
fun ActiveConfigurationCard(
    apiSettings: ApiSettings,
    modifier: Modifier = Modifier,
) {
    val transcriptionLabel = SettingsStatusLabels.transcriptionLabel(apiSettings)
    val llmLabel = SettingsStatusLabels.postProcessingLabel(apiSettings)
    val llmActive = SettingsStatusLabels.postProcessingActive(apiSettings)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Active configuration",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
            StatusRow(isActive = true, label = "Transcription", value = transcriptionLabel)
            StatusRow(isActive = llmActive, label = "Post-processing", value = llmLabel)
        }
    }
}

@Composable
private fun StatusRow(isActive: Boolean, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = if (isActive) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isActive) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Re-test every configured provider and show per-provider progress. */
@Composable
fun RetestProvidersCard(
    running: Boolean,
    progress: Map<String, ConnectionTester.RetestRowState>,
    onRetestAll: () -> Unit,
    onClearProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Re-test all configured providers",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Runs the same connection probe Settings exposes per provider, " +
                    "for every transcription + LLM provider with a stored key " +
                    "(plus local providers when the model file is on disk). " +
                    "Refreshes the green ✓ / stale badges in the keyboard pickers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onRetestAll,
                enabled = !running,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (running) "Retesting…" else "Re-test all")
            }

            if (progress.isNotEmpty()) {
                progress.entries.sortedBy { it.key }.forEach { (key, state) ->
                    RetestRow(key = key, state = state)
                }
                // Clear the per-row progress list after a batch completes. Only
                // enabled when the run isn't in flight — clearing mid-run would
                // yank the user's live status display.
                if (!running) {
                    TextButton(
                        onClick = onClearProgress,
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Clear results", fontSize = 11.sp) }
                }
            }
        }
    }
}

@Composable
private fun RetestRow(key: String, state: ConnectionTester.RetestRowState) {
    val (kind, name) = key.split(":", limit = 2).let {
        if (it.size == 2) it[0] to it[1] else "" to key
    }
    val displayName = when (kind) {
        "asr" -> ApiProvider.entries.firstOrNull { it.name == name }
            ?.localizedDisplayName() ?: name
        "llm" -> LlmProvider.entries.firstOrNull { it.name == name }
            ?.localizedDisplayName() ?: name
        else -> name
    }
    val kindLabel = when (kind) {
        "asr" -> "ASR"
        "llm" -> "LLM"
        else -> ""
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                kindLabel,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state is ConnectionTester.RetestRowState.Error) {
                    Text(
                        state.message?.take(80) ?: "failed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            when (state) {
                is ConnectionTester.RetestRowState.Pending -> Text(
                    "queued",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is ConnectionTester.RetestRowState.Running ->
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                is ConnectionTester.RetestRowState.Ok ->
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "ok",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                is ConnectionTester.RetestRowState.Error ->
                    Icon(
                        imageVector = Icons.Filled.Cancel,
                        contentDescription = "failed",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
            }
        }
    }
}
