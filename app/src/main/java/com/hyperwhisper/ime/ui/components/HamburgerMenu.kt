package com.hyperwhisper.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hyperwhisper.localization.LocalStrings

/**
 * Hamburger menu component
 * Provides dropdown access to Settings, Logs (in techie mode), and Help/About
 */
@Composable
fun HamburgerMenu(
    context: Context,
    techieModeEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Hamburger icon button
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // Dropdown menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Settings
            DropdownMenuItem(
                text = { Text(strings.settings) },
                onClick = {
                    expanded = false
                    val intent = Intent(context, com.hyperwhisper.ui.settings.SettingsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = strings.settingsDesc,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )

            // Logs (only shown in techie mode)
            if (techieModeEnabled) {
                DropdownMenuItem(
                    text = { Text(strings.viewLogs) },
                    onClick = {
                        expanded = false
                        val intent = Intent(context, com.hyperwhisper.ui.logs.LogsActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = strings.viewLogsDesc,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                )
            }

            // Help & About
            DropdownMenuItem(
                text = { Text(strings.helpAndAbout) },
                onClick = {
                    expanded = false
                    val intent = Intent(context, com.hyperwhisper.ui.about.AboutActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Help,
                        contentDescription = strings.helpAndAboutDesc,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            )
        }
    }
}
