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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.PopupProperties
import com.hyperwhisper.localization.LocalStrings

/**
 * Hamburger menu component
 * Provides dropdown access to Logs (in techie mode) and Help/About
 * Optimized to prevent blinking/flickering when opening/closing
 */
@Composable
fun HamburgerMenu(
    context: Context,
    techieModeEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }

    // Stabilize context to prevent recomposition
    val currentContext by rememberUpdatedState(context)

    Box(modifier = modifier) {
        // Hamburger icon button
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // Dropdown menu - only compose when expanded to prevent blinking
        if (expanded) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    clippingEnabled = false
                )
            ) {
                // Logs (only shown in techie mode)
                if (techieModeEnabled) {
                    DropdownMenuItem(
                        text = { Text(strings.viewLogs) },
                        onClick = {
                            expanded = false
                            val intent = Intent(currentContext, com.hyperwhisper.ui.logs.LogsActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            currentContext.startActivity(intent)
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
                        val intent = Intent(currentContext, com.hyperwhisper.ui.about.AboutActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        currentContext.startActivity(intent)
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
}
