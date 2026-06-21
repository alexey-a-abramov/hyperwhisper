package com.hyperwhisper.ui.sections

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.hyperwhisper.data.TranscriptionHistoryItem
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.KeyboardMetrics
import com.hyperwhisper.ui.KeyboardSpaceColor
import com.hyperwhisper.ui.components.LongPressIndicator

/**
 * Shared geometry for the universal keyboard bottom bar: every layout
 * (Dictation, Agent, Emoji, QWERTY/Code) renders [Paste-last] [Space]
 * [Enter] at the same height with the same screen positions, so muscle
 * memory transfers regardless of which mode the user is in.
 *
 * Values delegate to [KeyboardMetrics] so the bottom bar scales together
 * with the rest of the keyboard. Kept as named aliases here since several
 * call sites still import these specific symbols.
 */
val KeyboardBottomBarHeight: androidx.compose.ui.unit.Dp get() = KeyboardMetrics.BottomBarHeight
val KeyboardBottomBarSpacing: androidx.compose.ui.unit.Dp get() = KeyboardMetrics.BottomBarSpacing
val KeyboardEnterKeyWidth: androidx.compose.ui.unit.Dp get() = KeyboardMetrics.EnterKeyWidth

/**
 * Universal keyboard bottom bar. Sits flush at the bottom of every layout.
 *
 * Slots:
 *  - Paste-last pill (auto-hidden when no transcription history). Tap pastes
 *    the most recent text; long-press opens the history panel.
 *  - Space (canonical yellow, weighted to fill).
 *  - Enter (fixed [KeyboardEnterKeyWidth] so the screen position is identical
 *    across every layout).
 */
@Composable
fun KeyboardBottomBar(
    lastTranscribedText: String,
    transcriptionHistory: List<TranscriptionHistoryItem>,
    enableHistoryPanel: Boolean,
    onPasteText: (String) -> Unit,
    onShowHistory: () -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Paste-last "Insert" moved to the top header (universal strip /
    // dictation header), so the bottom bar is now just Space + Enter.
    Row(
        modifier = modifier.fillMaxWidth().height(KeyboardBottomBarHeight),
        horizontalArrangement = Arrangement.spacedBy(KeyboardBottomBarSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomBarSpace(weight = 1f, onClick = onSpace)
        BottomBarEnter(onClick = onEnter)
    }
}

internal fun hasPasteContent(
    lastTranscribedText: String,
    transcriptionHistory: List<TranscriptionHistoryItem>
): Boolean = lastTranscribedText.isNotEmpty() || transcriptionHistory.isNotEmpty()

/**
 * Compact "Insert" chip for the top headers (universal strip + dictation
 * header): clipboard icon + a short preview of [pasteText]. Tap pastes it;
 * long-press opens history. Renders nothing when [pasteText] is blank. The
 * caller sizes it via [modifier].
 */
@Composable
fun InsertChip(
    pasteText: String,
    enableHistoryPanel: Boolean,
    onPaste: (String) -> Unit,
    onShowHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pasteText.isEmpty()) return
    val strings = LocalStrings.current
    Box(modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enableHistoryPanel) {
                    detectTapGestures(
                        onTap = { onPaste(pasteText) },
                        onLongPress = { if (enableHistoryPanel) onShowHistory() }
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = KeyboardMetrics.BaseUnit * 2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = strings.pasteLastTranscription,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (pasteText.length > 12) pasteText.take(12) + "…" else pasteText,
                    fontSize = 10.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                )
            }
        }
        if (enableHistoryPanel) LongPressIndicator(padding = 3.dp)
    }
}

@Composable
fun RowScope.BottomBarSpace(
    weight: Float = 1f,
    onClick: () -> Unit
) {
    val strings = LocalStrings.current
    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight(),
        shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = KeyboardSpaceColor,
            contentColor = Color.Black
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = strings.space,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFeatureSettings = "smcp"
            )
        )
    }
}

@Composable
fun BottomBarEnter(
    onClick: () -> Unit
) {
    val strings = LocalStrings.current
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
        modifier = Modifier
            .width(KeyboardEnterKeyWidth)
            .fillMaxHeight()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardReturn,
                contentDescription = strings.enterDesc,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
