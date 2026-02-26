package com.hyperwhisper.ui.selectors

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    recentSelections: List<ProviderModelSelection>,
    onProviderModelSelected: (ApiProvider, String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current

    val preferredProviderOrder = remember(currentProvider, recentSelections) {
        val recentProviders = recentSelections.map { it.provider }.distinct()
        val ordered = mutableListOf<ApiProvider>()
        ordered.add(currentProvider)
        ordered.addAll(recentProviders.filter { it != currentProvider })
        ordered.addAll(ApiProvider.entries.filter { it !in ordered })
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 16.dp
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Provider + Model",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chips.forEach { selection ->
                        AssistChip(
                            onClick = {
                                onProviderModelSelected(selection.provider, selection.modelId)
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

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(preferredProviderOrder.size) { index ->
                        val provider = preferredProviderOrder[index]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (provider == currentProvider) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = provider.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                provider.defaultModels.forEach { model ->
                                    Surface(
                                        onClick = { onProviderModelSelected(provider, model) },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = if (provider == currentProvider && model == currentModelId) {
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = model,
                                                fontSize = 12.sp,
                                                fontWeight = if (provider == currentProvider && model == currentModelId) {
                                                    FontWeight.Bold
                                                } else {
                                                    FontWeight.Normal
                                                }
                                            )
                                            if (provider == currentProvider && model == currentModelId) {
                                                Text(
                                                    text = "Selected",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                                )
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
                        .height(40.dp)
                ) {
                    Text(strings.cancel.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
