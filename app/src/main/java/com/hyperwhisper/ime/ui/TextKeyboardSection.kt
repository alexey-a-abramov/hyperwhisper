package com.hyperwhisper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.KeyboardInputMode
import com.hyperwhisper.data.RecordingState
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.buttons.PeriodKeyWithPopup

@Composable
internal fun TextKeyboardSectionNew(
    mode: KeyboardInputMode,
    layout: com.hyperwhisper.data.KeyboardLayout = com.hyperwhisper.data.KeyboardLayout.ENGLISH,
    recordingState: RecordingState = RecordingState.IDLE,
    recordingDuration: Long = 0L,
    onModeChange: (KeyboardInputMode) -> Unit,
    onKeyPress: (String) -> Unit,
    onSpacePress: () -> Unit,
    onSpaceLongPress: () -> Unit = {},
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
    onInsert: () -> Unit = {},
    onForwardDelete: () -> Unit = {},
    onEscape: () -> Unit = {},
    onTab: () -> Unit = {},
    onReturnToDictation: () -> Unit,
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    modifierState: com.hyperwhisper.ime.keyboard.ModifierKeyState.State =
        com.hyperwhisper.ime.keyboard.ModifierKeyState.State(),
    onToggleCtrl: () -> Unit = {},
    onToggleAlt: () -> Unit = {},
    onToggleShift: () -> Unit = {},
    onLockCtrl: () -> Unit = {},
    onLockAlt: () -> Unit = {},
    onLockShift: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var shiftEnabled by remember { mutableStateOf(false) }
    var capsLockEnabled by remember { mutableStateOf(false) }
    var ctrlSticky by remember { mutableStateOf(false) }
    var altSticky by remember { mutableStateOf(false) }
    var shiftSticky by remember { mutableStateOf(false) }

    val letterCase: (String) -> String = { key ->
        when {
            capsLockEnabled -> key.uppercase()
            shiftEnabled && key.all { it.isLetter() } -> key.uppercase()
            else -> key
        }
    }

    val isSpecialChars = mode == KeyboardInputMode.SPECIAL_CHARS

    // Get the layout definition
    val layoutDef = KeyboardLayouts.getLayout(layout)

    val topRows = if (isSpecialChars) {
        listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("[", "]", "{", "}", "(", ")", "<", ">", "/", "\\"),
            listOf("+", "-", "*", "=", "==", "!=", "&", "|", "&&", "||"),
            listOf("%", "^", "~", "`", ":", ";", "\"", "'", "?", ".")
        )
    } else {
        listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            layoutDef.topRow,
            layoutDef.middleRow
        )
    }

    val bottomRowKeys = if (isSpecialChars) emptyList() else layoutDef.bottomRow

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
                modifier = modifier
            )
        }
        KeyboardInputMode.NUMPAD -> {
            // Classic numpad layout with F-keys and sticky modifiers
            Surface(
                modifier = modifier.fillMaxWidth().fillMaxHeight(),
                color = KeyboardSurfaceColor,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // F-keys row 1 (F1-F6)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        (1..6).forEach { num ->
                            KeyboardKeyButton(
                                label = "F$num",
                                onClick = {
                                    // Send F-key escape sequences
                                    val escapeSeq = when(num) {
                                        1 -> "\u001BOP"
                                        2 -> "\u001BOQ"
                                        3 -> "\u001BOR"
                                        4 -> "\u001BOS"
                                        5 -> "\u001B[15~"
                                        6 -> "\u001B[17~"
                                        else -> ""
                                    }
                                    onKeyPress(escapeSeq)
                                },
                                modifier = Modifier.weight(1f),
                                height = 32.dp
                            )
                        }
                    }

                    // F-keys row 2 (F7-F12)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        (7..12).forEach { num ->
                            KeyboardKeyButton(
                                label = "F$num",
                                onClick = {
                                    val escapeSeq = when(num) {
                                        7 -> "\u001B[18~"
                                        8 -> "\u001B[19~"
                                        9 -> "\u001B[20~"
                                        10 -> "\u001B[21~"
                                        11 -> "\u001B[23~"
                                        12 -> "\u001B[24~"
                                        else -> ""
                                    }
                                    onKeyPress(escapeSeq)
                                },
                                modifier = Modifier.weight(1f),
                                height = 32.dp
                            )
                        }
                    }

                    // Numpad row 1: Esc / * -
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        KeyboardActionButton(
                            label = "Esc",
                            onClick = onEscape,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                        KeyboardKeyButton(
                            label = "/",
                            onClick = {
                                val key = if (shiftSticky || altSticky || ctrlSticky) "\\" else "/"
                                onKeyPress(key)
                                shiftSticky = false
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 40.dp
                        )
                        KeyboardKeyButton(
                            label = "*",
                            onClick = {
                                val key = if (shiftSticky) "×" else if (altSticky) "·" else "*"
                                onKeyPress(key)
                                shiftSticky = false
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 40.dp
                        )
                        KeyboardKeyButton(
                            label = "-",
                            onClick = {
                                val key = if (shiftSticky) "_" else if (altSticky) "–" else "-"
                                onKeyPress(key)
                                shiftSticky = false
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 40.dp
                        )
                    }

                    // Numpad row 2: 7(Home) 8(↑) 9(PgUp) +
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        KeyboardKeyButton(
                            label = "7\nHome",
                            onClick = {
                                if (altSticky || ctrlSticky) onHome()
                                else onKeyPress("7")
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 45.dp
                        )
                        KeyboardKeyButton(
                            label = "8\n↑",
                            onClick = {
                                if (altSticky || ctrlSticky) onMoveCursorUp()
                                else onKeyPress("8")
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 45.dp
                        )
                        KeyboardKeyButton(
                            label = "9\nPgUp",
                            onClick = {
                                if (altSticky || ctrlSticky) onPageUp()
                                else onKeyPress("9")
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 45.dp
                        )
                        KeyboardKeyButton(
                            label = "+",
                            onClick = {
                                val key = if (shiftSticky) "≈" else if (altSticky) "±" else "+"
                                onKeyPress(key)
                                shiftSticky = false
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 45.dp
                        )
                    }

                    // Numpad row 3: 4(←) 5 6(→) =
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        KeyboardKeyButton(
                            label = "4\n←",
                            onClick = {
                                if (altSticky || ctrlSticky) onMoveCursorLeft()
                                else onKeyPress("4")
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 45.dp
                        )
                        KeyboardKeyButton(
                            label = "5",
                            onClick = {
                                onKeyPress("5")
                                shiftSticky = false
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 45.dp
                        )
                        KeyboardKeyButton(
                            label = "6\n→",
                            onClick = {
                                if (altSticky || ctrlSticky) onMoveCursorRight()
                                else onKeyPress("6")
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 45.dp
                        )
                        KeyboardKeyButton(
                            label = "=",
                            onClick = {
                                val key = if (shiftSticky) "≠" else if (altSticky) "≡" else "="
                                onKeyPress(key)
                                shiftSticky = false
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 45.dp
                        )
                    }

                    // Numpad row 4: 1(End) 2(↓) 3(PgDn) Enter (tall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(3f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                KeyboardKeyButton(
                                    label = "1\nEnd",
                                    onClick = {
                                        if (altSticky || ctrlSticky) onEnd()
                                        else onKeyPress("1")
                                        altSticky = false
                                ctrlSticky = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    height = 45.dp
                                )
                                KeyboardKeyButton(
                                    label = "2\n↓",
                                    onClick = {
                                        if (altSticky || ctrlSticky) onMoveCursorDown()
                                        else onKeyPress("2")
                                        altSticky = false
                                ctrlSticky = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    height = 45.dp
                                )
                                KeyboardKeyButton(
                                    label = "3\nPgDn",
                                    onClick = {
                                        if (altSticky || ctrlSticky) onPageDown()
                                        else onKeyPress("3")
                                        altSticky = false
                                ctrlSticky = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    height = 45.dp
                                )
                            }

                            // Numpad row 5: 0 . Del
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                KeyboardKeyButton(
                                    label = "0\nIns",
                                    onClick = {
                                        if (altSticky || ctrlSticky) onInsert()
                                        else onKeyPress("0")
                                        altSticky = false
                                ctrlSticky = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    height = 45.dp
                                )
                                KeyboardKeyButton(
                                    label = ".",
                                    onClick = {
                                        val key = if (shiftSticky) "," else "."
                                        onKeyPress(key)
                                        shiftSticky = false
                                altSticky = false
                                ctrlSticky = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    height = 45.dp
                                )
                                KeyboardActionButton(
                                    label = "Del",
                                    onClick = onForwardDelete,
                                    modifier = Modifier.weight(1f),
                                    style = KeyboardActionStyle.BACKSPACE,
                                    height = 45.dp
                                )
                            }
                        }

                        // Enter button (spans 2 rows on the right)
                        KeyboardActionButton(
                            label = "Enter",
                            onClick = onEnter,
                            modifier = Modifier.weight(1f).height(92.dp),
                            style = KeyboardActionStyle.ENTER
                        )
                    }

                    // Sticky modifiers row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Ctrl (sticky)
                        Surface(
                            onClick = { ctrlSticky = !ctrlSticky },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (ctrlSticky) MaterialTheme.colorScheme.primary else KeyboardKeyColor
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Ctrl",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (ctrlSticky) Color.White else KeyboardKeyTextColor
                                )
                            }
                        }

                        // Alt (sticky)
                        Surface(
                            onClick = { altSticky = !altSticky },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (altSticky) MaterialTheme.colorScheme.primary else KeyboardKeyColor
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Alt",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (altSticky) Color.White else KeyboardKeyTextColor
                                )
                            }
                        }

                        // Shift (sticky)
                        Surface(
                            onClick = { shiftSticky = !shiftSticky },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (shiftSticky) MaterialTheme.colorScheme.primary else KeyboardKeyColor
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Shift",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (shiftSticky) Color.White else KeyboardKeyTextColor
                                )
                            }
                        }

                        // Tab
                        KeyboardActionButton(
                            label = "Tab",
                            onClick = onTab,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                    }

                    // Bottom row with mode switcher, space, and backspace
                    Row(
                        modifier = Modifier.fillMaxWidth().height(45.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Unified mode switcher
                        UnifiedModeSwitcher(
                            currentMode = mode,
                            onModeChange = onModeChange,
                            onReturnToDictation = onReturnToDictation,
                            modifier = Modifier.weight(2.5f).fillMaxHeight()
                        )

                        KeyboardActionButton(
                            label = "space",
                            onClick = onSpacePress,
                            modifier = Modifier.weight(2f),
                            style = KeyboardActionStyle.SPACE
                        )

                        RepeatingActionButton(
                            icon = Icons.Default.Backspace,
                            onAction = onDelete,
                            style = KeyboardActionStyle.BACKSPACE,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        KeyboardInputMode.SYSTEM_KEYS -> {
            // System keys layout
            Surface(
                modifier = modifier.fillMaxWidth().fillMaxHeight(),
                color = KeyboardSurfaceColor,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // F-keys row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        (1..12).forEach { num ->
                            KeyboardKeyButton(
                                label = "F$num",
                                onClick = { onKeyPress("\u001B[$num~") },
                                modifier = Modifier.weight(1f),
                                height = 40.dp
                            )
                        }
                    }

                    // Navigation row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        KeyboardActionButton(
                            label = "HOME",
                            onClick = onHome,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                        KeyboardActionButton(
                            label = "END",
                            onClick = onEnd,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                        KeyboardActionButton(
                            label = "PG↑",
                            onClick = onPageUp,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                        KeyboardActionButton(
                            label = "PG↓",
                            onClick = onPageDown,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                    }

                    // Cursor keys
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Spacer(Modifier.weight(1f))
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardArrowUp,
                            onClick = onMoveCursorUp,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                        Spacer(Modifier.weight(2f))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardArrowLeft,
                            onClick = onMoveCursorLeft,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardArrowDown,
                            onClick = onMoveCursorDown,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardArrowRight,
                            onClick = onMoveCursorRight,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                        Spacer(Modifier.weight(1f))
                    }

                    Spacer(Modifier.weight(1f))

                    // Bottom row with unified mode switcher
                    Row(
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Unified mode switcher (left)
                        UnifiedModeSwitcher(
                            currentMode = mode,
                            onModeChange = onModeChange,
                            onReturnToDictation = onReturnToDictation,
                            modifier = Modifier.weight(3f).fillMaxHeight()
                        )

                        KeyboardActionButton(
                            label = "space",
                            onClick = onSpacePress,
                            modifier = Modifier.weight(2.5f),
                            style = KeyboardActionStyle.SPACE
                        )
                        RepeatingActionButton(
                            icon = Icons.Default.Backspace,
                            onAction = onDelete,
                            style = KeyboardActionStyle.BACKSPACE,
                            modifier = Modifier.weight(1f)
                        )
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardReturn,
                            onClick = onEnter,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.ENTER
                        )
                    }
                }
            }
        }
        KeyboardInputMode.VIBE_CODING -> {
            // Vibe Coding mode - programmer's keyboard
            Surface(
                modifier = modifier.fillMaxWidth().fillMaxHeight(),
                color = KeyboardSurfaceColor,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val keyHeight = 36.dp

                    // Row 1: Common brackets and symbols
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf("{", "}", "[", "]", "(", ")", "<", ">", "/", "\\").forEach { key ->
                            KeyboardKeyButton(
                                label = key,
                                onClick = { onKeyPress(key) },
                                modifier = Modifier.weight(1f),
                                height = keyHeight
                            )
                        }
                    }

                    // Row 2: Special operators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf("&", "|", "^", "~", "!", "?", ":", ";", "=", "_").forEach { key ->
                            KeyboardKeyButton(
                                label = key,
                                onClick = { onKeyPress(key) },
                                modifier = Modifier.weight(1f),
                                height = keyHeight
                            )
                        }
                    }

                    // Row 3: More symbols
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf("@", "#", "$", "%", "*", "+", "-", ".", ",", "\"").forEach { key ->
                            KeyboardKeyButton(
                                label = key,
                                onClick = { onKeyPress(key) },
                                modifier = Modifier.weight(1f),
                                height = keyHeight
                            )
                        }
                    }

                    // Row 4: Navigation cluster with reorganized cursor/backspace/enter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        KeyboardActionButton(
                            label = "HOME",
                            onClick = onHome,
                            modifier = Modifier.weight(1.2f),
                            style = KeyboardActionStyle.NORMAL,
                            height = keyHeight
                        )
                        KeyboardActionButton(
                            label = "END",
                            onClick = onEnd,
                            modifier = Modifier.weight(1.2f),
                            style = KeyboardActionStyle.NORMAL,
                            height = keyHeight
                        )
                        KeyboardActionButton(
                            label = "PG↑",
                            onClick = onPageUp,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = keyHeight
                        )
                        KeyboardActionButton(
                            label = "PG↓",
                            onClick = onPageDown,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = keyHeight
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardArrowUp,
                            onClick = onMoveCursorUp,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = keyHeight
                        )
                        Spacer(modifier = Modifier.weight(0.5f))
                        RepeatingActionButton(
                            icon = Icons.Default.Backspace,
                            onAction = onDelete,
                            modifier = Modifier.weight(1.2f),
                            style = KeyboardActionStyle.BACKSPACE,
                            height = keyHeight
                        )
                    }

                    // Row 5: Tab, quotes, cursor controls (down positioned below up)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        KeyboardKeyButton(
                            label = "Tab",
                            onClick = { onKeyPress("\t") },
                            modifier = Modifier.weight(1.5f),
                            height = keyHeight
                        )
                        KeyboardKeyButton(
                            label = "'",
                            onClick = { onKeyPress("'") },
                            modifier = Modifier.weight(1f),
                            height = keyHeight
                        )
                        KeyboardKeyButton(
                            label = "`",
                            onClick = { onKeyPress("`") },
                            modifier = Modifier.weight(1f),
                            height = keyHeight
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardArrowLeft,
                            onClick = onMoveCursorLeft,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = keyHeight
                        )
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardArrowDown,
                            onClick = onMoveCursorDown,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = keyHeight
                        )
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardArrowRight,
                            onClick = onMoveCursorRight,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = keyHeight
                        )
                        Spacer(modifier = Modifier.weight(0.5f))
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardReturn,
                            onClick = onEnter,
                            modifier = Modifier.weight(1.2f),
                            style = KeyboardActionStyle.ENTER,
                            height = keyHeight
                        )
                    }

                    // Row 6: Recording controls (cancel + mic)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Unified mode switcher (left)
                        UnifiedModeSwitcher(
                            currentMode = mode,
                            onModeChange = onModeChange,
                            onReturnToDictation = onReturnToDictation,
                            modifier = Modifier.weight(2.5f).height(40.dp)
                        )

                        Spacer(modifier = Modifier.weight(3f))

                        // Cancel recording button (only shown when recording)
                        if (recordingState == RecordingState.RECORDING ||
                            recordingState == RecordingState.RECORDING_COMPLETE_AWAITING_CONFIRMATION) {
                            KeyboardActionButton(
                                icon = Icons.Default.Close,
                                onClick = { /* Cancel recording */ },
                                modifier = Modifier.weight(1f),
                                style = KeyboardActionStyle.NORMAL,
                                height = 40.dp
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        // Recording button for voice input in coding mode
                        Surface(
                            onClick = {
                                when (recordingState) {
                                    RecordingState.IDLE, RecordingState.ERROR -> onStartRecording()
                                    RecordingState.RECORDING, RecordingState.RECORDING_COMPLETE_AWAITING_CONFIRMATION -> onStopRecording()
                                    else -> {}
                                }
                            },
                            modifier = Modifier.weight(1.3f).height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = when (recordingState) {
                                RecordingState.RECORDING, RecordingState.RECORDING_COMPLETE_AWAITING_CONFIRMATION ->
                                    Color(0xFFE53935) // Red when recording
                                RecordingState.PROCESSING -> MaterialTheme.colorScheme.tertiary
                                else -> Color(0xFF4CAF50) // Green when idle
                            }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                when (recordingState) {
                                    RecordingState.RECORDING, RecordingState.RECORDING_COMPLETE_AWAITING_CONFIRMATION -> {
                                        // Show timer when recording
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Stop,
                                                contentDescription = strings.stopRecording,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            val seconds = (recordingDuration / 1000) % 60
                                            val minutes = (recordingDuration / 1000) / 60
                                            Text(
                                                text = "$minutes:${seconds.toString().padStart(2, '0')}",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    RecordingState.PROCESSING -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = strings.startRecording,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Bottom row: Space bar
                    Row(
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        KeyboardActionButton(
                            label = "space",
                            onClick = onSpacePress,
                            modifier = Modifier.fillMaxWidth(),
                            style = KeyboardActionStyle.SPACE
                        )
                    }
                }
            }
        }
        else -> {
            // QWERTY and SPECIAL_CHARS layouts
            Surface(
                modifier = modifier.fillMaxWidth().fillMaxHeight(),
                color = KeyboardSurfaceColor,
                shape = RoundedCornerShape(10.dp)
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val horizontalGap = 2.dp
                    val verticalGap = 4.dp
                    // Calculate total rows: topRows + shiftRow + bottomRow
                    val totalRows = if (isSpecialChars) {
                        topRows.size + 1 + 1 // topRows + symbols row + bottom row
                    } else {
                        topRows.size + 1 + 1 // topRows + shift row + bottom row
                    }
                    val totalVerticalGaps = verticalGap * (totalRows + 1)
                    val availableHeight = maxHeight - totalVerticalGaps
                    val keyHeight = (availableHeight / totalRows).coerceIn(32.dp, 48.dp)

                    Column(
                        modifier = Modifier.fillMaxSize().padding(4.dp),
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
                                    } else {
                                        // Number-row long-press → shifted symbol.
                                        val altSymbol: String? = if (!isSpecialChars) when (key) {
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
                                        } else null
                                        KeyboardKeyButton(
                                            label = if (isSpecialChars) key else letterCase(key),
                                            onClick = {
                                                val out = if (isSpecialChars) key else letterCase(key)
                                                onKeyPress(out)
                                                if (shiftEnabled && !isSpecialChars && key.all { it.isLetter() }) {
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

                        if (isSpecialChars) {
                            // Special characters row
                            Row(
                                modifier = Modifier.fillMaxWidth().height(keyHeight),
                                horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(",", "_", "@", "#", "$", "€", "£", "¥", "§", "⌫").forEach { key ->
                                    if (key == "⌫") {
                                        RepeatingActionButton(
                                            icon = Icons.Default.Backspace,
                                            onAction = onDelete,
                                            style = KeyboardActionStyle.BACKSPACE,
                                            modifier = Modifier.weight(1f),
                                            height = keyHeight
                                        )
                                    } else {
                                        KeyboardKeyButton(
                                            label = key,
                                            onClick = { onKeyPress(key) },
                                            modifier = Modifier.weight(1f),
                                            height = keyHeight
                                        )
                                    }
                                }
                            }
                        } else {
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
                                RepeatingActionButton(
                                    icon = Icons.Default.Backspace,
                                    onAction = onDelete,
                                    modifier = Modifier.weight(1.5f),
                                    style = KeyboardActionStyle.BACKSPACE,
                                    height = keyHeight
                                )
                            }
                        }

                        // Bottom row — mode switching is in the universal
                        // top strip; bottom row is now pure typing keys.
                        Row(
                            modifier = Modifier.fillMaxWidth().height(keyHeight),
                            horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Comma
                            KeyboardKeyButton(
                                label = ",",
                                onClick = { onKeyPress(",") },
                                modifier = Modifier.weight(0.7f),
                                height = keyHeight
                            )
                            // Space bar (long-press for layout selector)
                            LongPressActionButton(
                                label = "space",
                                onClick = onSpacePress,
                                onLongPress = onSpaceLongPress,
                                modifier = Modifier.weight(5.5f),
                                style = KeyboardActionStyle.SPACE,
                                height = keyHeight,
                                longPressThreshold = 800L
                            )
                            // Period — long-press shows Gboard-style char popup.
                            PeriodKeyWithPopup(
                                onKeyPress = onKeyPress,
                                modifier = Modifier.weight(0.7f),
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
