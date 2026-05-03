package com.hyperwhisper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.AgentCommand
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.util.repeatOnHold

/**
 * Generic agent quick-command keyboard. Renders a list of [AgentCommand] as
 * a tap-to-insert grid plus the canonical bottom row (space / enter / bksp).
 * The same composable powers Claude Code, OpenCode, Gemini and Codex modes —
 * only the command list differs.
 */
@Composable
fun AgentKeyboard(
    title: String,
    commands: List<AgentCommand>,
    onInsert: (String) -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = KeyboardSurfaceColor,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Section label so the user knows which agent they're priming.
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFB0B0B0),
                modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp)
            )

            // Command grid. Three columns gives reasonable touch targets for
            // ~12-18 commands per agent. Scrollable if the list is longer.
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(commands) { cmd ->
                    AgentCommandKey(cmd = cmd, onClick = { onInsert(cmd.insertion) })
                }
            }

            // Standard bottom row — same shape/colors as every other layout's
            // action keys so muscle memory transfers. Space yellow, enter green,
            // backspace red.
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ActionKey(
                    label = "space",
                    weight = 3f,
                    bg = KeyboardSpaceColor,
                    fg = Color.Black,
                    onClick = onSpace
                )
                ActionIconKey(
                    icon = Icons.Default.KeyboardReturn,
                    desc = strings.keyboardEnterDesc,
                    weight = 1f,
                    bg = KeyboardEnterColor,
                    fg = Color.White,
                    onClick = onEnter
                )
                ActionIconKey(
                    icon = Icons.Default.Backspace,
                    desc = strings.keyboardBackspaceDesc,
                    weight = 1f,
                    bg = KeyboardBackspaceColor,
                    fg = Color.White,
                    onClick = onDelete,
                    repeatOnHold = true
                )
            }
        }
    }
}

@Composable
private fun AgentCommandKey(cmd: AgentCommand, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = KeyboardKeyColor,
        tonalElevation = 1.dp,
        modifier = Modifier.height(46.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = cmd.label,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = KeyboardKeyTextColor,
                maxLines = 1
            )
            cmd.description?.let {
                Text(
                    text = it,
                    fontSize = 8.sp,
                    color = Color(0xFF666666),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ActionKey(
    label: String,
    weight: Float,
    bg: Color,
    fg: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = bg,
        modifier = Modifier.weight(weight).fillMaxHeight()
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ActionIconKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    weight: Float,
    bg: Color,
    fg: Color,
    onClick: () -> Unit,
    repeatOnHold: Boolean = false
) {
    val baseModifier = Modifier.weight(weight).fillMaxHeight()
    if (repeatOnHold) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = bg,
            modifier = baseModifier.repeatOnHold(onTrigger = onClick)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = desc, tint = fg, modifier = Modifier.size(18.dp))
            }
        }
    } else {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(6.dp),
            color = bg,
            modifier = baseModifier
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = desc, tint = fg, modifier = Modifier.size(18.dp))
            }
        }
    }
}
