package com.hyperwhisper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hyperwhisper.data.KeyboardInputMode
import com.hyperwhisper.ui.buttons.AccentKeyWithPopup
import com.hyperwhisper.ui.buttons.AccentMap
import com.hyperwhisper.ui.buttons.LocalityKey
import com.hyperwhisper.ui.buttons.PeriodKeyWithPopup
import com.hyperwhisper.ui.sections.PasteLastPill

/**
 * Typing layouts for the non-dictation modes. [mode] is normalized via
 * [KeyboardInputMode.normalize] before rendering, so only two layouts exist
 * here: CODE (delegated to [CodeKeyboard]) and the QWERTY-style letter board
 * (everything else). The legacy NUMPAD / SYSTEM_KEYS / VIBE_CODING /
 * SPECIAL_CHARS renderers were removed once normalize() made them
 * unreachable — old persisted modes collapse into CODE or QWERTY.
 */
@Composable
internal fun TextKeyboardSectionNew(
    mode: KeyboardInputMode,
    layout: com.hyperwhisper.data.KeyboardLayout = com.hyperwhisper.data.KeyboardLayout.ENGLISH,
    onKeyPress: (String) -> Unit,
    onSpacePress: () -> Unit,
    onEnterLongPress: () -> Unit = {},
    onDelete: () -> Unit,
    onEnter: () -> Unit,
    onMoveCursorLeft: () -> Unit,
    onMoveCursorRight: () -> Unit,
    onMoveCursorUp: () -> Unit,
    onMoveCursorDown: () -> Unit,
    onPageUp: () -> Unit,
    onPageDown: () -> Unit,
    onHome: () -> Unit,
    onEnd: () -> Unit,
    onEscape: () -> Unit = {},
    onTab: () -> Unit = {},
    modifierState: com.hyperwhisper.ime.keyboard.ModifierKeyState.State =
        com.hyperwhisper.ime.keyboard.ModifierKeyState.State(),
    onToggleCtrl: () -> Unit = {},
    onToggleAlt: () -> Unit = {},
    onToggleShift: () -> Unit = {},
    onLockCtrl: () -> Unit = {},
    onLockAlt: () -> Unit = {},
    onLockShift: () -> Unit = {},
    lastTranscribedText: String = "",
    transcriptionHistory: List<com.hyperwhisper.data.TranscriptionHistoryItem> = emptyList(),
    enableHistoryPanel: Boolean = false,
    onPasteText: (String) -> Unit = {},
    onShowHistory: () -> Unit = {},
    localityCode: String = "",
    onCycleLocality: () -> Unit = {},
    onShowLocalityList: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var shiftEnabled by remember { mutableStateOf(false) }
    var capsLockEnabled by remember { mutableStateOf(false) }

    val letterCase: (String) -> String = { key ->
        when {
            capsLockEnabled -> key.uppercase()
            shiftEnabled && key.all { it.isLetter() } -> key.uppercase()
            else -> key
        }
    }

    // Get the layout definition
    val layoutDef = KeyboardLayouts.getLayout(layout)

    val topRows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        layoutDef.topRow,
        layoutDef.middleRow
    )

    val bottomRowKeys = layoutDef.bottomRow

    when (mode.normalize()) {
        KeyboardInputMode.CODE -> {
            CodeKeyboard(
                onKeyPress = onKeyPress,
                onSpacePress = onSpacePress,
                onDelete = onDelete,
                onEnter = onEnter,
                onTab = onTab,
                onEscape = onEscape,
                onMoveCursorLeft = onMoveCursorLeft,
                onMoveCursorRight = onMoveCursorRight,
                onMoveCursorUp = onMoveCursorUp,
                onMoveCursorDown = onMoveCursorDown,
                onHome = onHome,
                onEnd = onEnd,
                onPageUp = onPageUp,
                onPageDown = onPageDown,
                modifierState = modifierState,
                onToggleCtrl = onToggleCtrl,
                onToggleAlt = onToggleAlt,
                onToggleShift = onToggleShift,
                onLockCtrl = onLockCtrl,
                onLockAlt = onLockAlt,
                onLockShift = onLockShift,
                lastTranscribedText = lastTranscribedText,
                transcriptionHistory = transcriptionHistory,
                enableHistoryPanel = enableHistoryPanel,
                onPasteText = onPasteText,
                onShowHistory = onShowHistory,
                modifier = modifier
            )
        }
        else -> {
            // QWERTY layout
            Surface(
                modifier = modifier.fillMaxWidth().fillMaxHeight(),
                color = KeyboardSurfaceColor,
                shape = RoundedCornerShape(KeyboardMetrics.SurfaceRadius)
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val horizontalGap = KeyboardMetrics.KeySpacing
                    // QWERTY's between-row gap is wider than the generic
                    // KeyboardMetrics.RowGap — the letters are taller and
                    // benefit from a touch more vertical breathing room.
                    // Kept as twice RowGap so it still scales together.
                    val verticalGap = KeyboardMetrics.RowGap * 2
                    // Calculate total rows: topRows + shiftRow + bottomRow
                    val totalRows = topRows.size + 1 + 1
                    val totalVerticalGaps = verticalGap * (totalRows + 1)
                    val availableHeight = maxHeight - totalVerticalGaps
                    val keyHeight = (availableHeight / totalRows)
                        .coerceIn(KeyboardMetrics.KeyHeightFloor, KeyboardMetrics.KeyHeightCeiling)

                    Column(
                        modifier = Modifier.fillMaxSize().padding(KeyboardMetrics.OuterPadding),
                        verticalArrangement = Arrangement.spacedBy(verticalGap)
                    ) {
                        // Number row
                        topRows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().height(keyHeight),
                                horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                row.forEach { key ->
                                    if (key.isEmpty()) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    } else if (AccentMap.accentsFor(key).isNotEmpty()) {
                                        // Letter with diacritic variants → hold for
                                        // the Gboard-style accent popup (à á â ä …).
                                        // Accents are pre-cased to match shift/caps.
                                        AccentKeyWithPopup(
                                            baseChar = letterCase(key),
                                            accents = AccentMap.accentsFor(key).map { letterCase(it) },
                                            onKeyPress = { out ->
                                                onKeyPress(out)
                                                if (shiftEnabled && !capsLockEnabled) shiftEnabled = false
                                            },
                                            modifier = Modifier.weight(1f),
                                            height = keyHeight
                                        )
                                    } else {
                                        // Number-row long-press → shifted symbol.
                                        val altSymbol: String? = when (key) {
                                            "1" -> "!"
                                            "2" -> "@"
                                            "3" -> "#"
                                            "4" -> "$"
                                            "5" -> "%"
                                            "6" -> "^"
                                            "7" -> "&"
                                            "8" -> "*"
                                            "9" -> "("
                                            "0" -> ")"
                                            else -> null
                                        }
                                        KeyboardKeyButton(
                                            label = letterCase(key),
                                            onClick = {
                                                onKeyPress(letterCase(key))
                                                if (shiftEnabled && key.all { it.isLetter() }) {
                                                    shiftEnabled = false
                                                }
                                            },
                                            longPressLabel = altSymbol,
                                            onLongPress = altSymbol?.let { sym -> { onKeyPress(sym) } },
                                            modifier = Modifier.weight(1f),
                                            height = keyHeight
                                        )
                                    }
                                }
                            }
                        }

                        // Shift row (bottom row keys with shift and backspace)
                        Row(
                            modifier = Modifier.fillMaxWidth().height(keyHeight),
                            horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            KeyboardActionButton(
                                icon = if (capsLockEnabled) Icons.Default.KeyboardCapslock else Icons.Default.ArrowUpward,
                                onClick = {
                                    if (shiftEnabled) {
                                        capsLockEnabled = true
                                        shiftEnabled = false
                                    } else if (capsLockEnabled) {
                                        capsLockEnabled = false
                                    } else {
                                        shiftEnabled = true
                                    }
                                },
                                modifier = Modifier.weight(1.5f),
                                style = KeyboardActionStyle.NORMAL,
                                height = keyHeight
                            )
                            bottomRowKeys.forEach { key ->
                                if (AccentMap.accentsFor(key).isNotEmpty()) {
                                    AccentKeyWithPopup(
                                        baseChar = letterCase(key),
                                        accents = AccentMap.accentsFor(key).map { letterCase(it) },
                                        onKeyPress = { out ->
                                            onKeyPress(out)
                                            if (shiftEnabled && !capsLockEnabled) shiftEnabled = false
                                        },
                                        modifier = Modifier.weight(1f),
                                        height = keyHeight
                                    )
                                } else {
                                    KeyboardKeyButton(
                                        label = letterCase(key),
                                        onClick = {
                                            onKeyPress(letterCase(key))
                                            if (shiftEnabled && !capsLockEnabled) shiftEnabled = false
                                        },
                                        modifier = Modifier.weight(1f),
                                        height = keyHeight
                                    )
                                }
                            }
                            RepeatingActionButton(
                                icon = Icons.Default.Backspace,
                                onAction = onDelete,
                                modifier = Modifier.weight(1.5f),
                                style = KeyboardActionStyle.BACKSPACE,
                                height = keyHeight
                            )
                        }

                        // Bottom row — universal paste-last pill (when there's
                        // history) at the leftmost slot for muscle-memory
                        // parity with Dictation/Agent/Emoji/Code, then the
                        // typing-friendly comma/space/period/enter cluster.
                        // Mode switching + backspace live in the universal
                        // top strip.
                        val hasPasteContent = lastTranscribedText.isNotEmpty() ||
                            transcriptionHistory.isNotEmpty()
                        Row(
                            modifier = Modifier.fillMaxWidth().height(keyHeight),
                            horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PasteLastPill(
                                lastTranscribedText = lastTranscribedText,
                                transcriptionHistory = transcriptionHistory,
                                enableHistoryPanel = enableHistoryPanel,
                                onPasteText = onPasteText,
                                onShowHistory = onShowHistory,
                                weight = 2.0f,
                            )
                            // Locality switcher — same control as the dictation
                            // row so layout/language switching works while
                            // typing too. Tap cycles, long-press lists.
                            LocalityKey(
                                code = localityCode,
                                onClick = onCycleLocality,
                                onLongClick = onShowLocalityList,
                                modifier = Modifier.weight(0.7f).fillMaxHeight()
                            )
                            // Comma — fixed PunctKeyWidth so it matches the
                            // dictation row's comma/period exactly.
                            KeyboardKeyButton(
                                label = ",",
                                onClick = { onKeyPress(",") },
                                modifier = Modifier.width(KeyboardMetrics.PunctKeyWidth),
                                height = keyHeight
                            )
                            // Space bar. Reduce weight when paste-last is
                            // present to keep the row visually balanced.
                            KeyboardActionButton(
                                label = "space",
                                onClick = onSpacePress,
                                modifier = Modifier.weight(if (hasPasteContent) 3.5f else 5.5f),
                                style = KeyboardActionStyle.SPACE,
                                height = keyHeight
                            )
                            // Period — shared hold-to-grid punctuation popup,
                            // same component and width as the dictation row.
                            PeriodKeyWithPopup(
                                onKeyPress = onKeyPress,
                                modifier = Modifier.width(KeyboardMetrics.PunctKeyWidth),
                                height = keyHeight
                            )
                            // Enter/Return (long-press for action selector)
                            LongPressActionButton(
                                icon = Icons.Default.KeyboardReturn,
                                onClick = onEnter,
                                onLongPress = onEnterLongPress,
                                modifier = Modifier.weight(1f),
                                style = KeyboardActionStyle.ENTER,
                                height = keyHeight,
                                longPressThreshold = 800L
                            )
                        }
                    }
                }
            }
        }
    }
}
