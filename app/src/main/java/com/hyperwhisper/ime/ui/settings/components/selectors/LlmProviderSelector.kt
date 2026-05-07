package com.hyperwhisper.ui.settings.components.selectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import com.hyperwhisper.data.LlmProvider
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.network.ConnectionTester
import com.hyperwhisper.ui.util.ProviderStatusChip
import com.hyperwhisper.ui.util.localizedDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmProviderSelector(
    selectedProvider: LlmProvider,
    onProviderSelected: (LlmProvider) -> Unit,
    modifier: Modifier = Modifier,
    lastTestedAt: Map<LlmProvider, Long> = emptyMap(),
    retestProgress: Map<String, ConnectionTester.RetestRowState> = emptyMap(),
) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = selectedProvider.localizedDisplayName(),
                onValueChange = {},
                readOnly = true,
                label = { Text(strings.selectorLlmProviderLabel) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor()
                    .weight(1f)
            )
            ProviderStatusChip(
                testedAt = lastTestedAt[selectedProvider],
                retestState = retestProgress["llm:${selectedProvider.name}"],
            )
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            LlmProvider.values().forEach { provider ->
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
                                retestState = retestProgress["llm:${provider.name}"],
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
