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
import com.hyperwhisper.localization.LocalStrings

/**
 * Top controls row of the keyboard
 * Layout: Keyboard Switcher | Settings | View Logs (techie mode) | Help
 */
@Composable
fun TopControlsRow(
    context: Context,
    showKeyboardSwitcher: Boolean,
    techieModeEnabled: Boolean,
    onSwitchKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Keyboard Switcher button (only shown if enabled in settings)
        if (showKeyboardSwitcher) {
            IconButton(onClick = onSwitchKeyboard) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = strings.switchKeyboardDesc,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Settings button (always visible)
        IconButton(
            onClick = {
                val intent = Intent(context, com.hyperwhisper.ui.settings.SettingsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = strings.settings,
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // View Logs button (only shown in techie mode)
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
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Help & About button (always visible)
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
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
