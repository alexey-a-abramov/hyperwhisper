package com.hyperwhisper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.KeyboardInputMode

/**
 * Universal top strip rendered above every non-DICTATION keyboard layout.
 *
 * Provides three guarantees:
 *  - You can always see the current mode (no more "what layout am I in?").
 *  - You can always exit to Voice with one tap (no more swipe-trapped).
 *  - The three most common dev shortcuts (Esc / Tab / Backspace) are always
 *    one tap away, regardless of which layout is active.
 *
 * 36dp tall.
 */
@Composable
fun UniversalKeyboardTopStrip(
    currentMode: KeyboardInputMode,
    cycleOrder: List<KeyboardInputMode>,
    onSelectMode: (KeyboardInputMode) -> Unit,
    onReturnToVoice: () -> Unit,
    onEsc: () -> Unit,
    onTab: () -> Unit,
    onBackspace: () -> Unit,
    onModePillTap: () -> Unit = {
        // Default: advance to next mode (legacy behavior). Caller wires this
        // to a dropdown menu when they want it.
        val idx = cycleOrder.indexOf(currentMode.normalize()).coerceAtLeast(0)
        onSelectMode(cycleOrder[(idx + 1) % cycleOrder.size])
    },
    onSettings: (() -> Unit)? = null,
    onHelp: (() -> Unit)? = null,
    onLogs: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val normalized = currentMode.normalize()
    val inVoice = normalized == KeyboardInputMode.DICTATION

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Voice fast-return — only meaningful when NOT already in voice.
            // In voice mode this chip is redundant noise.
            if (!inVoice) {
                ChipButton(
                    onClick = onReturnToVoice,
                    bg = MaterialTheme.colorScheme.primaryContainer,
                    fg = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Back to voice",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Mode pill — tap opens a dropdown menu (caller hosts the overlay
            // since IMEs can't render Compose Popup/Dialog due to window
            // token limitations).
            Surface(
                onClick = onModePillTap,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        normalized.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "▾",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Settings / Help / Logs — same place across every layout, no
            // separate top-controls row.
            if (onSettings != null) {
                IconChip(onClick = onSettings, icon = Icons.Default.Settings, desc = "Settings")
            }
            if (onLogs != null) {
                IconChip(onClick = onLogs, icon = Icons.Default.Assignment, desc = "Logs")
            }
            if (onHelp != null) {
                IconChip(onClick = onHelp, icon = Icons.Default.HelpOutline, desc = "Help")
            }

            // Universal dev shortcuts.
            ChipButton(onClick = onEsc) { Text("Esc", fontSize = 11.sp) }
            ChipButton(onClick = onTab) { Text("Tab", fontSize = 11.sp) }
            ChipButton(
                onClick = onBackspace,
                bg = MaterialTheme.colorScheme.errorContainer,
                fg = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "Backspace",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun IconChip(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxHeight()
            .size(width = 36.dp, height = 32.dp)
    ) {
        Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = desc, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ChipButton(
    onClick: () -> Unit,
    bg: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    fg: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
