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

/**
 * Typing layouts for the non-dictation modes. [mode] is normalized via
 * [KeyboardInputMode.normalize] before rendering, so only two layouts exist
 * here: CODE (delegated to [CodeKeyboard]) and the QWERTY-style letter board
 * (everything else). The legacy NUMPAD / SYSTEM_KEYS / VIBE_CODING /
 * SPECIAL_CHARS renderers were removed once normalize() made them
 * unreachable — old persisted modes collapse into CODE or QWERTY.
 */
/** A keyboard row plus the half-key indent applied to each side (0 = full width). */
private data class KeyRow(val keys: List<String>, val indentWeight: Float)

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

    // Letter rows are centred Gboard-style: when a letter row is shorter than
    // its sibling (English a–l = 9 vs q–p = 10), it's indented half a key on
    // each side so the keys line up in a grid and keep the same width as the
    // row above, instead of stretching to fill the board. The number row keeps
    // full width.
    val maxLetters = maxOf(layoutDef.topRow.size, layoutDef.middleRow.size)
    val keyRows = listOf(
        KeyRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), indentWeight = 0f),
        KeyRow(layoutDef.topRow, indentWeight = (maxLetters - layoutDef.topRow.size) / 2f),
        KeyRow(layoutDef.middleRow, indentWeight = (maxLetters - layoutDef.middleRow.size) / 2f),
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
                    val totalRows = keyRows.size + 1 + 1
                    val totalVerticalGaps = verticalGap * (totalRows + 1)
                    val availableHeight = maxHeight - totalVerticalGaps
                    val keyHeight = (availableHeight / totalRows)
                        .coerceIn(KeyboardMetrics.KeyHeightFloor, KeyboardMetrics.KeyHeightCeiling)

                    Column(
                        modifier = Modifier.fillMaxSize().padding(KeyboardMetrics.OuterPadding),
                        verticalArrangement = Arrangement.spacedBy(verticalGap)
                    ) {
                        // Number row + letter rows (shorter letter rows centred)
                        keyRows.forEach { kbRow ->
                            Row(
                                modifier = Modifier.fillMaxWidth().height(keyHeight),
                                horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (kbRow.indentWeight > 0f) {
                                    Spacer(modifier = Modifier.weight(kbRow.indentWeight))
                                }
                                kbRow.keys.forEach { key ->
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
                                if (kbRow.indentWeight > 0f) {
                                    Spacer(modifier = Modifier.weight(kbRow.indentWeight))
                                }
                            }
                        }

                        // Letter row — just the letters + backspace now. Shift
                        // moved down to the action row (left of the language
                        // control), which hands its old width to these letters.
                        Row(
                            modifier = Modifier.fillMaxWidth().height(keyHeight),
                            horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                // Matched to the shift key above (1.25) — see note there.
                                modifier = Modifier.weight(1.25f),
                                style = KeyboardActionStyle.BACKSPACE,
                                height = keyHeight
                            )
                        }

                        // Action row: Shift · language · space · period · enter.
                        // The comma key is gone (comma now lives on the period
                        // long-press), and the paste-last "Insert" pill moved up
                        // to the universal top strip — leaving a clean cluster.
                        Row(
                            modifier = Modifier.fillMaxWidth().height(keyHeight),
                            horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Shift — moved down from the letter row so it sits
                            // under the thumb, left of the language control.
                            // Same one-shot → caps-lock → off state machine.
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
                                modifier = Modifier.weight(1.25f),
                                style = KeyboardActionStyle.NORMAL,
                                height = keyHeight
                            )
                            // Language / locality switcher — wider than before.
                            // Tap cycles the enabled localities (and points
                            // dictation at that language); long-press lists them.
                            LocalityKey(
                                code = localityCode,
                                onClick = onCycleLocality,
                                onLongClick = onShowLocalityList,
                                modifier = Modifier.weight(1.5f).fillMaxHeight()
                            )
                            // Space bar — takes the slack freed by the dropped
                            // comma + paste pill.
                            KeyboardActionButton(
                                label = "space",
                                onClick = onSpacePress,
                                modifier = Modifier.weight(4f),
                                style = KeyboardActionStyle.SPACE,
                                height = keyHeight
                            )
                            // Period — shared hold-to-grid punctuation popup
                            // (comma is its first alternate).
                            PeriodKeyWithPopup(
                                onKeyPress = onKeyPress,
                                modifier = Modifier.width(KeyboardMetrics.PunctKeyWidth),
                                height = keyHeight
                            )
                            // Enter/Return (long-press for action selector).
                            // Matched to Shift's width so the row's ends balance.
                            LongPressActionButton(
                                icon = Icons.Default.KeyboardReturn,
                                onClick = onEnter,
                                onLongPress = onEnterLongPress,
                                modifier = Modifier.weight(1.25f),
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
