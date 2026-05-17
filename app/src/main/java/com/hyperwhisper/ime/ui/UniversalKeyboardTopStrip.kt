package com.hyperwhisper.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Backspace
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
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.components.LongPressIndicator
import com.hyperwhisper.ui.util.localizedDisplayName
import com.hyperwhisper.ui.util.repeatOnHold

/**
 * Universal top strip rendered above every keyboard layout.
 *
 * Provides:
 *  - Three keyboard switches always visible — Voice (mic), QWERTY (A) and a
 *    user-configurable preset slot. Tap on the preset switches to whatever
 *    mode it's bound to; long-press opens a picker so the user can rebind it.
 *  - Three universal dev shortcuts (Esc / Tab / Backspace) one tap away.
 *  - Settings + Logs entry points.
 *
 * Height + chip widths are read from [KeyboardMetrics] so the strip scales
 * with the rest of the keyboard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UniversalKeyboardTopStrip(
    currentMode: KeyboardInputMode,
    presetMode: KeyboardInputMode,
    onSelectMode: (KeyboardInputMode) -> Unit,
    onPresetLongPress: () -> Unit,
    onEsc: () -> Unit,
    onTab: () -> Unit,
    onBackspace: () -> Unit,
    onSettings: (() -> Unit)? = null,
    onLogs: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val normalized = currentMode.normalize()
    val normalizedPreset = presetMode.normalize().let {
        // Voice and QWERTY have dedicated buttons — never bind them to the
        // configurable slot or the user can't escape the third button.
        if (it == KeyboardInputMode.DICTATION || it == KeyboardInputMode.QWERTY)
            KeyboardInputMode.CODE else it
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(KeyboardMetrics.TopStripHeight),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = KeyboardMetrics.OuterPadding,
                    vertical = KeyboardMetrics.RowGap
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KeyboardMetrics.TopStripKeyGap)
        ) {
            // Slot 1 — Voice (Dictation).
            ModeSlot(
                isSelected = normalized == KeyboardInputMode.DICTATION,
                onClick = { onSelectMode(KeyboardInputMode.DICTATION) }
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = strings.keyboardBackToVoiceDesc,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Slot 2 — QWERTY ("A" label, plain text per spec).
            ModeSlot(
                isSelected = normalized == KeyboardInputMode.QWERTY,
                onClick = { onSelectMode(KeyboardInputMode.QWERTY) }
            ) {
                Text(
                    "A",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Slot 3 — configurable preset. Fixed width so any label fits
            // (longest is "Claude Code"). Tap = activate, long-press = rebind.
            // When current mode IS the preset, render highlighted.
            val presetIsCurrent = normalized == normalizedPreset
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = KeyboardMetrics.RowGap)
                    .width(KeyboardMetrics.ModeChipWidth)
            ) {
                Surface(
                    color = if (presetIsCurrent)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (presetIsCurrent)
                        MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
                    modifier = Modifier
                        .fillMaxSize()
                        .combinedClickable(
                            onClick = { onSelectMode(normalizedPreset) },
                            onLongClick = onPresetLongPress
                        )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = normalizedPreset.localizedDisplayName(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
                LongPressIndicator(padding = 3.dp)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Settings / Logs — Help is intentionally absent; reachable from
            // Settings if needed.
            if (onSettings != null) {
                IconChip(onClick = onSettings, icon = Icons.Default.Settings, desc = strings.settingsDesc)
            }
            if (onLogs != null) {
                IconChip(onClick = onLogs, icon = Icons.Default.Assignment, desc = strings.keyboardLogsDesc)
            }

            // Universal dev shortcuts.
            ChipButton(onClick = onEsc) { Text("Esc", fontSize = 11.sp) }
            ChipButton(onClick = onTab) { Text("Tab", fontSize = 11.sp) }
            ChipButton(
                onClick = onBackspace,
                bg = MaterialTheme.colorScheme.errorContainer,
                fg = MaterialTheme.colorScheme.onErrorContainer,
                repeatOnHold = true
            ) {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = strings.keyboardBackspaceDesc,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeSlot(
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = KeyboardMetrics.RowGap)
            .width(KeyboardMetrics.TopStripIconWidth)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
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
        shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
        modifier = Modifier
            .fillMaxHeight()
            .width(KeyboardMetrics.TopStripIconWidth)
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
    repeatOnHold: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val baseModifier = Modifier
        .fillMaxHeight()
        .padding(vertical = KeyboardMetrics.RowGap)
    if (repeatOnHold) {
        Surface(
            color = bg,
            contentColor = fg,
            shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
            modifier = baseModifier.repeatOnHold(onTrigger = onClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = KeyboardMetrics.BaseUnit * 2.5f),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    } else {
        Surface(
            onClick = onClick,
            color = bg,
            contentColor = fg,
            shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
            modifier = baseModifier
        ) {
            Row(
                modifier = Modifier.padding(horizontal = KeyboardMetrics.BaseUnit * 2.5f),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}
