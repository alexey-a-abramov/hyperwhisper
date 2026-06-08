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
    Row(
        modifier = modifier.fillMaxWidth().height(KeyboardBottomBarHeight),
        horizontalArrangement = Arrangement.spacedBy(KeyboardBottomBarSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PasteLastPill(
            lastTranscribedText = lastTranscribedText,
            transcriptionHistory = transcriptionHistory,
            enableHistoryPanel = enableHistoryPanel,
            onPasteText = onPasteText,
            onShowHistory = onShowHistory
        )
        BottomBarSpace(
            // When the paste pill is hidden the space button expands to take
            // the full free width so the target stays large.
            weight = if (hasPasteContent(lastTranscribedText, transcriptionHistory)) 1.4f else 1f,
            onClick = onSpace
        )
        BottomBarEnter(onClick = onEnter)
    }
}

internal fun hasPasteContent(
    lastTranscribedText: String,
    transcriptionHistory: List<TranscriptionHistoryItem>
): Boolean = lastTranscribedText.isNotEmpty() || transcriptionHistory.isNotEmpty()

@Composable
fun RowScope.PasteLastPill(
    lastTranscribedText: String,
    transcriptionHistory: List<TranscriptionHistoryItem>,
    enableHistoryPanel: Boolean,
    onPasteText: (String) -> Unit,
    onShowHistory: () -> Unit,
    weight: Float = 1f,
) {
    if (!hasPasteContent(lastTranscribedText, transcriptionHistory)) return
    val strings = LocalStrings.current
    val textToShow = if (lastTranscribedText.isNotEmpty()) lastTranscribedText
        else transcriptionHistory.first().text
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enableHistoryPanel) {
                    detectTapGestures(
                        onTap = { onPasteText(textToShow) },
                        onLongPress = {
                            if (enableHistoryPanel) onShowHistory()
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = KeyboardMetrics.BaseUnit * 2.5f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = strings.pasteLastTranscription,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (textToShow.length > 24) textToShow.take(24) + "…" else textToShow,
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                )
            }
        }
        if (enableHistoryPanel) LongPressIndicator(padding = 4.dp)
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
