package com.hyperwhisper.ui.settings.components.selectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.network.ConnectionTester
import com.hyperwhisper.ui.util.ProviderStatusChip
import com.hyperwhisper.ui.util.localizedDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudProviderSelector(
    selectedProvider: ApiProvider,
    onProviderSelected: (ApiProvider) -> Unit,
    modifier: Modifier = Modifier,
    lastTestedAt: Map<ApiProvider, Long> = emptyMap(),
    retestProgress: Map<String, ConnectionTester.RetestRowState> = emptyMap(),
) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = strings.provider,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            // Inline status chip (right of the field) — shows the active
            // provider's tested-status / live retest progress so the user
            // sees test history for the provider currently configured even
            // when the dropdown is collapsed.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = selectedProvider.localizedDisplayName(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(strings.selectorProviderLabel) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .weight(1f)
                )
                ProviderStatusChip(
                    testedAt = lastTestedAt[selectedProvider],
                    retestState = retestProgress["asr:${selectedProvider.name}"],
                )
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                ApiProvider.entries.forEach { provider ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    provider.localizedDisplayName(),
                                    modifier = Modifier.weight(1f),
                                )
                                ProviderStatusChip(
                                    testedAt = lastTestedAt[provider],
                                    retestState = retestProgress["asr:${provider.name}"],
                                )
                            }
                        },
                        onClick = {
                            onProviderSelected(provider)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
