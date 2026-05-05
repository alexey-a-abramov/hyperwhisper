package com.hyperwhisper.ui.sections

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import com.hyperwhisper.ui.components.LongPressIndicator

/**
 * Bottom actions row of the keyboard.
 *
 * Layout: [Paste-last (chip with paste icon + truncated preview, long-press
 * for history)] [Space] [Enter]. Enter pinned at bottom-right matches every
 * other layout's bottom row, so the same screen position works regardless of
 * which keyboard mode the user is in.
 */
@Composable
fun BottomActionsRow(
    lastTranscribedText: String,
    transcriptionHistory: List<TranscriptionHistoryItem>,
    enableHistoryPanel: Boolean,
    onPasteText: (String) -> Unit,
    onShowHistory: () -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val rowHeight = 44.dp

    Row(
        modifier = modifier.fillMaxWidth().height(rowHeight),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val hasPasteContent = lastTranscribedText.isNotEmpty() || transcriptionHistory.isNotEmpty()

        if (hasPasteContent) {
            val textToShow = if (lastTranscribedText.isNotEmpty()) lastTranscribedText
                else transcriptionHistory.first().text
            // Compact paste-last pill: paste icon + preview text only. The
            // orange dot in the corner advertises the long-press history
            // affordance — the verbose "(HOLD: HISTORY)" caption is gone.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
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
                            .padding(horizontal = 10.dp),
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

        // Space — same canonical yellow as every other layout's spacebar.
        // When the paste pill is hidden it expands to take the full free
        // width so the space target stays large.
        Button(
            onClick = onSpace,
            modifier = Modifier
                .weight(if (hasPasteContent) 1.4f else 1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = com.hyperwhisper.ui.KeyboardSpaceColor,
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
                    fontFeatureSettings = "smcp" // Small caps
                )
            )
        }

        // Enter — pinned bottom-right, fixed width. Same screen position as
        // every other layout's enter, so muscle memory transfers.
        Surface(
            onClick = onEnter,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .width(60.dp)
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
}
