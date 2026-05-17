package com.hyperwhisper.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.AgentCategory
import com.hyperwhisper.data.AgentCommand
import com.hyperwhisper.data.KeyChord
import com.hyperwhisper.data.TranscriptionHistoryItem
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.components.LongPressIndicator
import com.hyperwhisper.ui.sections.KeyboardBottomBar

/**
 * Generic agent quick-command keyboard. Renders a list of [AgentCommand] as
 * a tap-to-insert grid plus the universal [KeyboardBottomBar] (paste-last /
 * space / enter). Backspace lives in the universal top strip on every
 * layout, so it's no longer duplicated here. The same composable powers
 * Claude Code, OpenCode, Gemini and Codex modes — only the command list
 * differs.
 */
@Composable
fun AgentKeyboard(
    title: String,
    commands: List<AgentCommand>,
    onInsert: (text: String, stayOnPalette: Boolean) -> Unit,
    onSendChord: (KeyChord) -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    lastTranscribedText: String,
    transcriptionHistory: List<TranscriptionHistoryItem>,
    enableHistoryPanel: Boolean,
    onPasteText: (String) -> Unit,
    onShowHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val grouped = commands.groupBy { it.category }
    val inline = grouped[AgentCategory.INLINE].orEmpty()
    // Render order for the grid sections — sigils handled separately above.
    val gridSections = listOf(
        AgentCategory.SESSION,
        AgentCategory.CODE,
        AgentCategory.MACRO,
        AgentCategory.META
    ).mapNotNull { cat ->
        grouped[cat]?.takeIf { it.isNotEmpty() }?.let { cat to it }
    }
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
            // Right side hosts a recall affordance — re-insert the last text
            // the user committed, so re-prompting (a frequent power-user move)
            // is a single tap instead of "swipe to QWERTY → tap Paste → swipe back".
            Row(
                modifier = Modifier.fillMaxWidth().height(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFB0B0B0),
                    modifier = Modifier.padding(start = 6.dp).weight(1f)
                )
                if (lastTranscribedText.isNotEmpty()) {
                    Surface(
                        // Recall is a one-shot — drop the user back into their
                        // typing layout after we insert the recalled text.
                        onClick = { onInsert(lastTranscribedText, false) },
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Transparent,
                        modifier = Modifier.padding(end = 4.dp).size(18.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Insert last sent text",
                                tint = Color(0xFFB0B0B0),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Pinned inline row — sigils + always-on keywords sit above the
            // scrolling grid so they're a single tap regardless of palette
            // length. Skipped silently if the palette has no INLINE entries.
            if (inline.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    inline.forEach { cmd ->
                        AgentCommandKey(
                            cmd = cmd,
                            onInsert = onInsert,
                            onSendChord = onSendChord,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Command grid, partitioned by category with small section headers
            // so the user can skim by intent (Session controls vs. Code actions
            // vs. Meta admin) instead of reading every label.
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                gridSections.forEach { (category, items) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        CategoryHeader(category)
                    }
                    if (category == AgentCategory.MACRO) {
                        // Macros are full-width pills — phrases read better with
                        // breathing room, and the palette is short so spending
                        // a row per item costs nothing.
                        items.forEach { cmd ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                AgentCommandKey(cmd = cmd, onInsert = onInsert, onSendChord = onSendChord)
                            }
                        }
                    } else {
                        items(items) { cmd ->
                            AgentCommandKey(cmd = cmd, onInsert = onInsert, onSendChord = onSendChord)
                        }
                    }
                }
            }

            // Universal bottom bar — same paste-last/space/enter as every
            // other layout. Backspace is in the top strip.
            KeyboardBottomBar(
                lastTranscribedText = lastTranscribedText,
                transcriptionHistory = transcriptionHistory,
                enableHistoryPanel = enableHistoryPanel,
                onPasteText = onPasteText,
                onShowHistory = onShowHistory,
                onSpace = onSpace,
                onEnter = onEnter
            )
        }
    }
}

@Composable
private fun CategoryHeader(category: AgentCategory) {
    val (label, color) = when (category) {
        AgentCategory.SESSION -> "SESSION" to Color(0xFF4DB6AC)
        AgentCategory.CODE -> "CODE" to Color(0xFFFFB74D)
        AgentCategory.META -> "MORE" to Color(0xFF9E9E9E)
        AgentCategory.MACRO -> "MACROS" to Color(0xFFB39DDB)
        AgentCategory.INLINE -> return // Inline lives in the pinned top row, no header.
    }
    Text(
        text = label,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 1.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AgentCommandKey(
    cmd: AgentCommand,
    onInsert: (text: String, stayOnPalette: Boolean) -> Unit,
    onSendChord: (KeyChord) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasVariants = cmd.variants.isNotEmpty()
    val onPrimary: () -> Unit = {
        // Chord wins if both are set — chord chips are explicit gestures and
        // shouldn't double-insert their (usually empty) text payload.
        cmd.keyChord?.let(onSendChord) ?: onInsert(cmd.insertion, cmd.stayOnPalette)
    }
    Box {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = KeyboardKeyColor,
            tonalElevation = 1.dp,
            modifier = modifier
                .height(36.dp)
                .combinedClickable(
                    onClick = onPrimary,
                    onLongClick = if (hasVariants) ({ menuExpanded = true }) else null
                )
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cmd.label,
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    // Slash commands and sigils are code-like (mono helps token
                    // recognition); macros are prose and read better in the
                    // default proportional family.
                    fontFamily = if (cmd.category == AgentCategory.MACRO) FontFamily.Default else FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = KeyboardKeyTextColor,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
                if (hasVariants) {
                    LongPressIndicator(padding = 2.dp, dotSize = 4.dp)
                }
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            cmd.variants.forEach { variant ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = variant.label,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        variant.keyChord?.let(onSendChord)
                            ?: onInsert(variant.insertion, variant.stayOnPalette)
                        menuExpanded = false
                    }
                )
            }
        }
    }
}

