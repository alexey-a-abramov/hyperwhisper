package com.hyperwhisper.ui

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
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = KeyboardSurfaceColor,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val keyHeight = 36.dp

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
                horizontalArrangement = Arrangement.spacedBy(2.dp)
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

            // 6. Modifiers + arrows + space + Enter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Modifiers — visual only this pass; sendKeyEvent wiring is the
                // next pass per the plan.
                CodeActionButton("Ctrl", { onKeyPress("") }, weight = 1.0f, height = keyHeight, dim = true)
                CodeActionButton("Alt", { onKeyPress("") }, weight = 1.0f, height = keyHeight, dim = true)
                CodeActionButton("Shift", { onKeyPress("") }, weight = 1.0f, height = keyHeight, dim = true)
                CodeIconButton(Icons.Default.KeyboardArrowLeft, "Left", onMoveCursorLeft, weight = 0.9f, height = keyHeight)
                CodeIconButton(Icons.Default.KeyboardArrowDown, "Down", onMoveCursorDown, weight = 0.9f, height = keyHeight)
                CodeIconButton(Icons.Default.KeyboardArrowRight, "Right", onMoveCursorRight, weight = 0.9f, height = keyHeight)
                CodeActionButton(
                    "space", onSpacePress, weight = 2.5f, height = keyHeight,
                    bg = KeyboardSpaceColor,
                    fg = Color.Black
                )
                CodeIconButton(
                    Icons.Default.KeyboardReturn, "Enter", onEnter,
                    weight = 1.3f, height = keyHeight,
                    bg = KeyboardEnterColor,
                    fg = Color.White
                )
            }
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
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        keys.forEach { key ->
            Surface(
                onClick = { onKeyPress(key) },
                modifier = Modifier
                    .weight(1f)
                    .height(height),
                shape = RoundedCornerShape(6.dp),
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
        shape = RoundedCornerShape(6.dp),
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
        shape = RoundedCornerShape(6.dp),
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
