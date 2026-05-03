package com.hyperwhisper.ui.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hyperwhisper.data.VoiceMode
import com.hyperwhisper.localization.LocalStrings

@Composable
fun AddModeDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    val strings = LocalStrings.current
    var name by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addVoiceMode) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.modeName) },
                    placeholder = { Text(strings.dialogVoiceModeNamePlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text(strings.systemPrompt) },
                    placeholder = { Text(strings.dialogVoiceModePromptPlaceholder) },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && systemPrompt.isNotBlank()) {
                        onAdd(name, systemPrompt)
                    }
                },
                enabled = name.isNotBlank() && systemPrompt.isNotBlank()
            ) {
                Text(strings.add)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun EditModeDialog(
    mode: VoiceMode,
    onDismiss: () -> Unit,
    onUpdate: (VoiceMode) -> Unit
) {
    val strings = LocalStrings.current
    var name by remember { mutableStateOf(mode.name) }
    var systemPrompt by remember { mutableStateOf(mode.systemPrompt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.editVoiceMode) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.modeName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text(strings.systemPrompt) },
                    placeholder = { Text(strings.enterPrompt) },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && systemPrompt.isNotBlank()) {
                        onUpdate(mode.copy(name = name, systemPrompt = systemPrompt))
                    }
                },
                enabled = name.isNotBlank() && systemPrompt.isNotBlank()
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
