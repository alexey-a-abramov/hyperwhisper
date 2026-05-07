package com.hyperwhisper.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.LlmProvider
import com.hyperwhisper.network.ConnectionTester
import com.hyperwhisper.ui.util.localizedDisplayName

/**
 * Modern settings home: status hero on top, category list below.
 * Tap a category → navigate into its detail screen.
 */
@Composable
fun SettingsHomeScreen(
    apiSettings: ApiSettings,
    onCategorySelected: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
    retestProgress: Map<String, ConnectionTester.RetestRowState> = emptyMap(),
    retestRunning: Boolean = false,
    onRetestAll: () -> Unit = {},
    onClearRetestProgress: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { StatusHeroCard(apiSettings) }
        item {
            RetestAllCard(
                running = retestRunning,
                progress = retestProgress,
                onRetestAll = onRetestAll,
                onClearProgress = onClearRetestProgress,
            )
        }
        item { Spacer(Modifier.height(4.dp)) }

        items(SettingsCategory.values()) { category ->
            CategoryRow(
                category = category,
                trailing = trailingTextFor(category, apiSettings),
                onClick = { onCategorySelected(category) }
            )
        }
    }
}

@Composable
private fun RetestAllCard(
    running: Boolean,
    progress: Map<String, ConnectionTester.RetestRowState>,
    onRetestAll: () -> Unit,
    onClearProgress: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                // Clear the per-row progress list after a batch completes.
                // Only enabled when the run isn't in flight — clearing mid-
                // run would yank the user's live status display.
                if (!running) {
                    androidx.compose.material3.TextButton(
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
        // Stack the name + (optional) error message in a Column so a long
        // provider name (e.g. "OpenAI-Compatible") doesn't get squeezed into
        // a one-character-per-line column when paired with a long error
        // string. Status icon stays right-aligned.
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
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (state is ConnectionTester.RetestRowState.Error) {
                    Text(
                        state.message?.take(80) ?: "failed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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

@Composable
private fun StatusHeroCard(apiSettings: ApiSettings) {
    val transcriptionLabel = SettingsStatusLabels.transcriptionLabel(apiSettings)
    val llmLabel = SettingsStatusLabels.postProcessingLabel(apiSettings)
    val llmActive = SettingsStatusLabels.postProcessingActive(apiSettings)

    Card(
        modifier = Modifier.fillMaxWidth(),
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
            StatusRow(
                isActive = true,
                label = "Transcription",
                value = transcriptionLabel
            )
            StatusRow(
                isActive = llmActive,
                label = "Post-processing",
                value = llmLabel
            )
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

@Composable
private fun CategoryRow(
    category: SettingsCategory,
    trailing: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            CategoryIcon(category.icon)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.localizedTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    category.localizedSubtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!trailing.isNullOrBlank()) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

private fun trailingTextFor(category: SettingsCategory, apiSettings: ApiSettings): String? =
    SettingsStatusLabels.categoryTrailing(category, apiSettings)
