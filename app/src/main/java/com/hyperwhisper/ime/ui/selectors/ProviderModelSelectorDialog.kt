package com.hyperwhisper.ui.selectors

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ProviderModelSelection
import com.hyperwhisper.localization.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderModelSelectorDialog(
    currentProvider: ApiProvider,
    currentModelId: String,
    configuredProviders: List<ApiProvider>,
    recentSelections: List<ProviderModelSelection>,
    onProviderModelSelected: (ApiProvider, String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    var modelPickerProvider by remember { mutableStateOf<ApiProvider?>(null) }
    var modelFilter by remember { mutableStateOf("") }
    var freeOnly by remember { mutableStateOf(false) }
    val availableProviders = remember(configuredProviders, currentProvider) {
        configuredProviders.distinct().ifEmpty { listOf(currentProvider) }
    }

    val preferredProviderOrder = remember(currentProvider, recentSelections, availableProviders) {
        val recentProviders = recentSelections.map { it.provider }.distinct()
        val ordered = mutableListOf<ApiProvider>()
        ordered.add(currentProvider)
        ordered.addAll(recentProviders.filter { it in availableProviders && it != currentProvider })
        ordered.addAll(availableProviders.filter { it !in ordered })
        ordered
    }

    val currentSelection = ProviderModelSelection(currentProvider, currentModelId)
    val chips = remember(currentSelection, recentSelections) {
        listOf(currentSelection) + recentSelections.filterNot {
            it.provider == currentSelection.provider && it.modelId == currentSelection.modelId
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header section
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "SELECT PROVIDER + MODEL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Choose your transcription provider and model",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Configured: ${availableProviders.size}/${ApiProvider.entries.size}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            // Recent selections chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chips.forEach { selection ->
                    AssistChip(
                        onClick = {
                            if (selection.provider in availableProviders) {
                                onProviderModelSelected(selection.provider, selection.modelId)
                            }
                        },
                        label = {
                            Text(
                                text = "${selection.provider.displayName}: ${selection.modelId}",
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            Divider()

            // Provider list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(preferredProviderOrder.size) { index ->
                    val provider = preferredProviderOrder[index]
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (provider == currentProvider) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        tonalElevation = if (provider == currentProvider) 6.dp else 2.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = provider.displayName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (provider == currentProvider) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            val providerModel = if (provider == currentProvider) {
                                currentModelId
                            } else {
                                recentSelections.firstOrNull { it.provider == provider }?.modelId
                                    ?: provider.defaultModels.firstOrNull().orEmpty()
                            }

                            Text(
                                text = "Current: $providerModel",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (provider == currentProvider) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onProviderModelSelected(provider, providerModel) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("SELECT", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { modelPickerProvider = provider },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("PICK MODEL", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom close button
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(strings.cancel.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    modelPickerProvider?.let { provider ->
        val showFreeFilter = provider == ApiProvider.OPENROUTER || provider == ApiProvider.HUGGINGFACE
        val filteredModels = remember(provider, modelFilter, freeOnly) {
            provider.defaultModels.filter { model ->
                val matchesText = modelFilter.isBlank() || model.contains(modelFilter, ignoreCase = true)
                val matchesFree = !showFreeFilter || !freeOnly || model.contains("free", ignoreCase = true)
                matchesText && matchesFree
            }
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { modelPickerProvider = null },
            title = { Text("Select model for ${provider.displayName}") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = modelFilter,
                        onValueChange = { modelFilter = it },
                        label = { Text("Filter models") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (showFreeFilter) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Checkbox(
                                checked = freeOnly,
                                onCheckedChange = { freeOnly = it }
                            )
                            Text(
                                text = "Free",
                                modifier = Modifier.padding(top = 14.dp)
                            )
                        }
                    }
                    filteredModels.forEach { model ->
                        TextButton(
                            onClick = {
                                onProviderModelSelected(provider, model)
                                modelPickerProvider = null
                                modelFilter = ""
                                freeOnly = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(model)
                        }
                    }
                    if (filteredModels.isEmpty()) {
                        Text(
                            text = "No matching models",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { modelPickerProvider = null }) {
                    Text(strings.cancel.uppercase())
                }
            }
        )
    }
}
