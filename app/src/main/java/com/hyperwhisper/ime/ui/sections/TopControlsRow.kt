package com.hyperwhisper.ui.sections

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hyperwhisper.data.RecordingState
import com.hyperwhisper.data.VoiceMode
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.selectors.ModeSelector

/**
 * Top controls row of the keyboard
 * Contains keyboard switcher, mode selector, logs button (techie mode), help, and settings
 */
@Composable
fun TopControlsRow(
    context: Context,
    voiceModes: List<VoiceMode>,
    selectedModeId: String,
    recordingState: RecordingState,
    showKeyboardSwitcher: Boolean,
    techieModeEnabled: Boolean,
    onSwitchKeyboard: () -> Unit,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Switch to Previous Keyboard button (only shown if enabled in settings)
        if (showKeyboardSwitcher) {
            IconButton(onClick = onSwitchKeyboard) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = strings.switchKeyboardDesc,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))
        }

        ModeSelector(
            modes = voiceModes,
            selectedModeId = selectedModeId,
            onModeSelected = onModeSelected,
            enabled = recordingState == RecordingState.IDLE,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Logs button (only shown in techie mode)
        if (techieModeEnabled) {
            IconButton(
                onClick = {
                    val intent = Intent(context, com.hyperwhisper.ui.logs.LogsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Assignment,
                    contentDescription = strings.viewLogsDesc,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // Help/About button
        IconButton(
            onClick = {
                val intent = Intent(context, com.hyperwhisper.ui.about.AboutActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        ) {
            Icon(
                imageVector = Icons.Default.Help,
                contentDescription = strings.helpAndAboutDesc,
                tint = MaterialTheme.colorScheme.secondary
            )
        }

        IconButton(
            onClick = {
                val intent = Intent(context, com.hyperwhisper.ui.settings.SettingsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = strings.settingsDesc,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
