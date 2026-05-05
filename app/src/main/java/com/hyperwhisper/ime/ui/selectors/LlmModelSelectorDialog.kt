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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.LlmProvider
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.util.localizedDisplayName

/**
 * Full-screen LLM provider + model picker for the IME context.
 *
 * IMEs can't host real Compose Dialogs (BadTokenException — the IME service
 * doesn't have an Activity window token), so we render as a Surface overlay
 * that fills the IME's compose tree. Same approach as
 * [ProviderModelSelectorDialog] for transcription.
 *
 * Two-pane behaviour: tap a provider to expand its model list inline, tap a
 * model to commit. Switching provider here changes only `provider` and
 * `modelId` in `LlmConfig` — API keys and custom base URLs still need to be
 * configured in Settings, since the data model holds a single LLM key, not a
 * per-provider map.
 */
@Composable
fun LlmModelSelectorDialog(
    currentProvider: LlmProvider,
    currentModelId: String,
    onProviderModelSelected: (LlmProvider, String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    var expandedProvider by remember { mutableStateOf<LlmProvider?>(currentProvider) }
    // "Configured" today means: providers that don't need a key (locals,
    // OpenAI-compatible) plus the currently-selected provider (we know it
    // has a key because it's the active one). Once per-provider LLM keys
    // exist this filter expands to anything with a stored key.
    val providers = remember(currentProvider) {
        LlmProvider.entries.filter {
            it != LlmProvider.NONE && (it == currentProvider || !it.requiresAuth)
        }
    }

    // No BackHandler — IMEs don't provide an OnBackPressedDispatcherOwner,
    // and calling BackHandler crashes with IllegalStateException. User
    // dismisses via the × close button.
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
                    text = "Post-processing LLM",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
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

            Text(
                text = "Switching provider keeps the existing API key — set " +
                    "the new provider's key in Settings if needed.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(providers) { provider ->
                    val isCurrent = provider == currentProvider
                    val isExpanded = provider == expandedProvider
                    Surface(
                        onClick = {
                            expandedProvider = if (isExpanded) null else provider
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
                                Text(
                                    text = provider.localizedDisplayName(),
                                    fontSize = 13.sp,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isCurrent)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCurrent) {
                                    Text(
                                        text = currentModelId,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            // Inline model list — only for the expanded
                            // provider, so the screen doesn't drown in 60+
                            // model entries at once.
                            if (isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 8.dp, bottom = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    provider.defaultModels.forEach { model ->
                                        val isCurrentModel = isCurrent && model == currentModelId
                                        Surface(
                                            onClick = {
                                                onProviderModelSelected(provider, model)
                                                onDismiss()
                                            },
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
                                                    text = model,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (isCurrentModel)
                                                        MaterialTheme.colorScheme.onPrimary
                                                    else
                                                        MaterialTheme.colorScheme.onSurface
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
