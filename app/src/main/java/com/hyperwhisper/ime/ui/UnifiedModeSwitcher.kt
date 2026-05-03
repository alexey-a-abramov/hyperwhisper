package com.hyperwhisper.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.KeyboardInputMode
import com.hyperwhisper.localization.LocalStrings

private val KeyboardModeSwitcherColor = Color(0xFF424242)

@Composable
internal fun UnifiedModeSwitcher(
    currentMode: KeyboardInputMode,
    onModeChange: (KeyboardInputMode) -> Unit,
    onReturnToDictation: () -> Unit,
    currentLayout: com.hyperwhisper.data.KeyboardLayout = com.hyperwhisper.data.KeyboardLayout.ENGLISH,
    onLayoutSelectorClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Layout button (shows 2-letter code like EN, RU, etc.)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            onLayoutSelectorClick?.invoke()
                        },
                        onTap = {
                            when (currentMode) {
                                KeyboardInputMode.QWERTY -> onModeChange(KeyboardInputMode.SPECIAL_CHARS)
                                KeyboardInputMode.SPECIAL_CHARS -> onModeChange(KeyboardInputMode.QWERTY)
                                else -> onModeChange(KeyboardInputMode.QWERTY)
                            }
                        }
                    )
                }
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp),
                color = if (currentMode == KeyboardInputMode.QWERTY || currentMode == KeyboardInputMode.SPECIAL_CHARS)
                    MaterialTheme.colorScheme.primary else KeyboardModeSwitcherColor
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentLayout.code,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Dictation button (with mic icon)
        Surface(
            onClick = onReturnToDictation,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(8.dp),
            color = if (currentMode == KeyboardInputMode.DICTATION)
                MaterialTheme.colorScheme.primary else KeyboardModeSwitcherColor
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = strings.keyboardDictationDesc,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Numpad button
        Surface(
            onClick = { onModeChange(KeyboardInputMode.NUMPAD) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(8.dp),
            color = if (currentMode == KeyboardInputMode.NUMPAD)
                MaterialTheme.colorScheme.primary else KeyboardModeSwitcherColor
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "123",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Vibe Coding button
        Surface(
            onClick = { onModeChange(KeyboardInputMode.VIBE_CODING) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(8.dp),
            color = if (currentMode == KeyboardInputMode.VIBE_CODING)
                MaterialTheme.colorScheme.primary else KeyboardModeSwitcherColor
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "</>",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
