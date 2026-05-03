package com.hyperwhisper.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hyperwhisper.data.AppearanceSettings
import com.hyperwhisper.ui.settings.dialogs.HistoryReductionWarningDialog

/**
 * Behavior settings extracted from Appearance: history, clipboard, audio files,
 * keyboard switcher, techie/dev toggles. Pure behavior — nothing visual.
 */
@Composable
fun KeyboardBehaviorSection(
    appearanceSettings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        GroupHeader("History")

        ToggleRow(
            title = "Enable history panel",
            description = "Long-press paste button to view recent transcriptions",
            checked = appearanceSettings.enableHistoryPanel,
            onChange = { onSettingsChange(appearanceSettings.copy(enableHistoryPanel = it)) }
        )

        if (appearanceSettings.enableHistoryPanel) {
            ToggleRow(
                title = "Unlimited history",
                description = "Keep all transcriptions (uses more storage)",
                checked = appearanceSettings.unlimitedHistory,
                onChange = { onSettingsChange(appearanceSettings.copy(unlimitedHistory = it)) }
            )

            if (!appearanceSettings.unlimitedHistory) {
                MaxHistoryField(
                    settings = appearanceSettings,
                    onChange = onSettingsChange
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        GroupHeader("Clipboard & audio")

        ToggleRow(
            title = "Auto-copy to clipboard",
            description = "Copy each transcription automatically",
            checked = appearanceSettings.autoCopyToClipboard,
            onChange = { onSettingsChange(appearanceSettings.copy(autoCopyToClipboard = it)) }
        )

        ToggleRow(
            title = "Save original audio",
            description = "Store recordings for playback and reprocessing",
            checked = appearanceSettings.saveOriginalAudioFiles,
            onChange = { onSettingsChange(appearanceSettings.copy(saveOriginalAudioFiles = it)) }
        )

        Spacer(Modifier.height(4.dp))
        GroupHeader("Keyboard")

        ToggleRow(
            title = "Show keyboard switcher",
            description = "Button to switch input method, next to mode selector",
            checked = appearanceSettings.showKeyboardSwitcher,
            onChange = { onSettingsChange(appearanceSettings.copy(showKeyboardSwitcher = it)) }
        )

        ToggleRow(
            title = "Per-app layout memory",
            description = "Restore the last layout you used in each app (e.g. Code in Termux, Voice in WhatsApp)",
            checked = appearanceSettings.perAppLayoutMemoryEnabled,
            onChange = { onSettingsChange(appearanceSettings.copy(perAppLayoutMemoryEnabled = it)) }
        )

        Spacer(Modifier.height(4.dp))
        GroupHeader("Coding agents")

        Text(
            "Add quick-command keyboards for CLI agents. Each enabled agent " +
                "appears as a separate mode in the keyboard's mode dropdown.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        com.hyperwhisper.data.KeyboardInputMode.agentModes.forEach { mode ->
            val key = mode.name
            val enabled = key in appearanceSettings.enabledAgentKeyboards
            ToggleRow(
                title = mode.displayName,
                description = "Quick-command keyboard for ${mode.displayName}",
                checked = enabled,
                onChange = { isOn ->
                    val newSet = appearanceSettings.enabledAgentKeyboards.toMutableSet().apply {
                        if (isOn) add(key) else remove(key)
                    }
                    onSettingsChange(appearanceSettings.copy(enabledAgentKeyboards = newSet))
                }
            )
        }

        Spacer(Modifier.height(4.dp))
        GroupHeader("Developer")

        ToggleRow(
            title = "Techie mode",
            description = "Show diagnostic info and logs button on the keyboard",
            checked = appearanceSettings.techieModeEnabled,
            onChange = { onSettingsChange(appearanceSettings.copy(techieModeEnabled = it)) }
        )
    }
}

@Composable
private fun MaxHistoryField(
    settings: AppearanceSettings,
    onChange: (AppearanceSettings) -> Unit
) {
    var text by remember(settings.maxHistoryItems) { mutableStateOf(settings.maxHistoryItems.toString()) }
    var showWarning by remember { mutableStateOf(false) }
    var pendingValue by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Maximum history items", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Number to keep (1–100)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { newValue ->
                text = newValue
                val newMax = newValue.toIntOrNull()
                if (newMax != null && newMax in 1..100) {
                    if (newMax < settings.maxHistoryItems) {
                        pendingValue = newMax
                        showWarning = true
                    } else {
                        onChange(settings.copy(maxHistoryItems = newMax))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = text.toIntOrNull()?.let { it !in 1..100 } ?: true
        )
    }

    if (showWarning) {
        HistoryReductionWarningDialog(
            currentSize = settings.maxHistoryItems,
            newSize = pendingValue,
            onConfirm = {
                onChange(settings.copy(maxHistoryItems = pendingValue))
                showWarning = false
            },
            onDismiss = {
                text = settings.maxHistoryItems.toString()
                showWarning = false
            }
        )
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
