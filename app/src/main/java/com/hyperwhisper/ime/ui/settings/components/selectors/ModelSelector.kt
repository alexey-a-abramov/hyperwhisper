package com.hyperwhisper.ui.settings.components.selectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.localization.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelector(
    provider: ApiProvider,
    selectedModel: String,
    availableModels: List<String>,
    onModelSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    var customModel by remember { mutableStateOf(selectedModel) }
    var filter by remember { mutableStateOf("") }
    var freeOnly by remember { mutableStateOf(false) }
    val isCustomModel = !availableModels.contains(selectedModel)
    val showFreeFilter = provider == ApiProvider.OPENROUTER || provider == ApiProvider.HUGGINGFACE
    val filteredModels = remember(availableModels, filter, freeOnly, showFreeFilter) {
        availableModels.filter { model ->
            val matchesText = filter.isBlank() || model.contains(filter, ignoreCase = true)
            val matchesFree = !showFreeFilter || !freeOnly || model.contains("free", ignoreCase = true)
            matchesText && matchesFree
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedModel,
            onValueChange = {
                customModel = it
                onModelSelected(it)
            },
            label = { Text(strings.modelId) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true,
            supportingText = {
                if (isCustomModel && selectedModel.isNotEmpty()) {
                    Text(strings.selectorCustomModelTag)
                }
            }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text(strings.selectorFilterModelsLabel) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                singleLine = true
            )
            if (showFreeFilter) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Checkbox(
                        checked = freeOnly,
                        onCheckedChange = { freeOnly = it }
                    )
                    Text(
                        text = strings.selectorFreeFilterLabel,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
            }

            filteredModels.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        onModelSelected(model)
                        customModel = model
                        expanded = false
                    }
                )
            }
            if (filteredModels.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(strings.selectorNoMatchingModels) },
                    onClick = {}
                )
            }
        }
    }
}
