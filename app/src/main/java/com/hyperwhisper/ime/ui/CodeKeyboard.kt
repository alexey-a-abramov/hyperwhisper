package com.hyperwhisper.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardReturn
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
import com.hyperwhisper.data.TranscriptionHistoryItem
import com.hyperwhisper.ui.sections.KeyboardBottomBar
import com.hyperwhisper.ui.util.repeatOnHold

/**
 * Compact developer keyboard. Replaces the three legacy layouts (NUMPAD,
 * SYSTEM_KEYS, VIBE_CODING) which all overlapped on content but presented
 * differently. Six rows at 36dp slot under the universal top strip in 320dp.
 *
 * Row inventory:
 *   1. Number row 0–9 (long-press shifts to !@#$%^&*() — wired in next pass)
 *   2. Brackets and slashes
 *   3. Common operators
 *   4. Punctuation + secondary symbols
 *   5. Nav + Esc/Tab/Bksp
 *   6. Modifiers (Ctrl/Alt/Shift) + space + arrows + Enter
 */
@Composable
fun CodeKeyboard(
    onKeyPress: (String) -> Unit,
    onSpacePress: () -> Unit,
    onDelete: () -> Unit,
    onEnter: () -> Unit,
    onTab: () -> Unit,
    onEscape: () -> Unit,
    onMoveCursorLeft: () -> Unit,
    onMoveCursorRight: () -> Unit,
    onMoveCursorUp: () -> Unit,
    onMoveCursorDown: () -> Unit,
    onHome: () -> Unit,
    onEnd: () -> Unit,
    onPageUp: () -> Unit,
    onPageDown: () -> Unit,
    modifierState: com.hyperwhisper.ime.keyboard.ModifierKeyState.State =
        com.hyperwhisper.ime.keyboard.ModifierKeyState.State(),
    onToggleCtrl: () -> Unit = {},
    onToggleAlt: () -> Unit = {},
    onToggleShift: () -> Unit = {},
    onLockCtrl: () -> Unit = {},
    onLockAlt: () -> Unit = {},
    onLockShift: () -> Unit = {},
    lastTranscribedText: String = "",
    transcriptionHistory: List<TranscriptionHistoryItem> = emptyList(),
    enableHistoryPanel: Boolean = false,
    onPasteText: (String) -> Unit = {},
    onShowHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = KeyboardSurfaceColor,
        shape = RoundedCornerShape(KeyboardMetrics.SurfaceRadius)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(KeyboardMetrics.OuterPadding),
            verticalArrangement = Arrangement.spacedBy(KeyboardMetrics.RowGap)
        ) {
            // CodeKeyboard runs 6 typing rows + bottom bar, so it uses the
            // Compact key height. QWERTY's 4-row layout uses Standard via
            // BoxWithConstraints derivation.
            val keyHeight = KeyboardMetrics.KeyHeightCompact

            // 1. Numbers
            CodeKeyRow(listOf("1","2","3","4","5","6","7","8","9","0"), keyHeight, onKeyPress)
            // 2. Brackets / slashes
            CodeKeyRow(listOf("{","}","[","]","(",")","<",">","/","\\"), keyHeight, onKeyPress)
            // 3. Operators
            CodeKeyRow(listOf("&","|","^","~","!","?","=","+","-","_"), keyHeight, onKeyPress)
            // 4. Punct & secondary
            CodeKeyRow(listOf("@","#","\$","%","*",":",";",".",",","\""), keyHeight, onKeyPress)

            // 5. Nav cluster + Esc / Tab / Backspace
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KeyboardMetrics.KeySpacing)
            ) {
                CodeActionButton("Esc", onEscape, weight = 1.1f, height = keyHeight)
                CodeActionButton("Tab", onTab, weight = 1.0f, height = keyHeight)
                CodeIconButton(Icons.Default.KeyboardArrowUp, "Up", onMoveCursorUp, weight = 1.0f, height = keyHeight)
                CodeActionButton("Home", onHome, weight = 1.1f, height = keyHeight)
                CodeActionButton("End", onEnd, weight = 1.1f, height = keyHeight)
                CodeActionButton("PgUp", onPageUp, weight = 1.1f, height = keyHeight)
                CodeActionButton("PgDn", onPageDown, weight = 1.1f, height = keyHeight)
                CodeIconButton(
                    Icons.Default.Backspace, "Backspace", onDelete,
                    weight = 1.3f, height = keyHeight,
                    bg = KeyboardBackspaceColor,
                    fg = Color.White
                )
            }

            // 6. Modifiers + arrows. Space and Enter migrated to row 7 (the
            // universal bottom bar) so the action keys land in the same
            // screen position they do on every other layout — muscle memory
            // transfers across QWERTY ↔ Code without re-aiming.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KeyboardMetrics.KeySpacing)
            ) {
                // Modifiers wired to ModifierKeyState. Tap = one-shot toggle
                // (auto-clears after the next keypress); long-press = lock
                // (stays on across multiple presses). The IME service consults
                // the state at commit time and dispatches sendKeyEvent with
                // proper meta flags so apps that honor InputConnection meta
                // (Termux, vim, IDEs) see real keychords.
                CodeModifierButton("Ctrl", modifierState.ctrl, modifierState.ctrlLocked,
                    onTap = onToggleCtrl, onLock = onLockCtrl, weight = 1.0f, height = keyHeight)
                CodeModifierButton("Alt", modifierState.alt, modifierState.altLocked,
                    onTap = onToggleAlt, onLock = onLockAlt, weight = 1.0f, height = keyHeight)
                CodeModifierButton("Shift", modifierState.shift, modifierState.shiftLocked,
                    onTap = onToggleShift, onLock = onLockShift, weight = 1.0f, height = keyHeight)
                CodeIconButton(Icons.Default.KeyboardArrowLeft, "Left", onMoveCursorLeft, weight = 1.0f, height = keyHeight)
                CodeIconButton(Icons.Default.KeyboardArrowDown, "Down", onMoveCursorDown, weight = 1.0f, height = keyHeight)
                CodeIconButton(Icons.Default.KeyboardArrowRight, "Right", onMoveCursorRight, weight = 1.0f, height = keyHeight)
            }

            // 7. Universal bottom bar — same paste-last/space/enter as every
            // other layout. Backspace stays on row 5 (the existing nav row)
            // since Code uses it constantly and shouldn't be top-strip-only.
            KeyboardBottomBar(
                lastTranscribedText = lastTranscribedText,
                transcriptionHistory = transcriptionHistory,
                enableHistoryPanel = enableHistoryPanel,
                onPasteText = onPasteText,
                onShowHistory = onShowHistory,
                onSpace = onSpacePress,
                onEnter = onEnter,
            )
        }
    }
}

@Composable
private fun CodeKeyRow(
    keys: List<String>,
    height: androidx.compose.ui.unit.Dp,
    onKeyPress: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeyboardMetrics.KeySpacing)
    ) {
        keys.forEach { key ->
            Surface(
                onClick = { onKeyPress(key) },
                modifier = Modifier
                    .weight(1f)
                    .height(height),
                shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
                color = KeyboardKeyColor,
                tonalElevation = 1.dp
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = key,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = KeyboardKeyTextColor
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CodeActionButton(
    label: String,
    onClick: () -> Unit,
    weight: Float,
    height: androidx.compose.ui.unit.Dp,
    bg: Color = KeyboardKeyColor,
    fg: Color = KeyboardKeyTextColor,
    dim: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .weight(weight)
            .height(height),
        shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
        color = if (dim) bg.copy(alpha = 0.7f) else bg,
        tonalElevation = 1.dp
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = fg,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CodeModifierButton(
    label: String,
    active: Boolean,
    locked: Boolean,
    onTap: () -> Unit,
    onLock: () -> Unit,
    weight: Float,
    height: androidx.compose.ui.unit.Dp
) {
    val bg = when {
        locked -> MaterialTheme.colorScheme.tertiary
        active -> MaterialTheme.colorScheme.primary
        else -> KeyboardKeyColor
    }
    val fg = when {
        locked -> MaterialTheme.colorScheme.onTertiary
        active -> MaterialTheme.colorScheme.onPrimary
        else -> KeyboardKeyTextColor
    }
    @OptIn(ExperimentalFoundationApi::class)
    Surface(
        shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
        color = bg,
        tonalElevation = 1.dp,
        modifier = Modifier
            .weight(weight)
            .height(height)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLock
            )
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (locked) "$label·" else label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = fg,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CodeIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    weight: Float,
    height: androidx.compose.ui.unit.Dp,
    bg: Color = KeyboardKeyColor,
    fg: Color = KeyboardKeyTextColor
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .weight(weight)
            .height(height),
        shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
        color = bg,
        tonalElevation = 1.dp
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = fg,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
