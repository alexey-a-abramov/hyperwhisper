package com.hyperwhisper.ui.sections

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.TranscriptionHistoryItem
import com.hyperwhisper.localization.LocalStrings

/**
 * Bottom actions row of the keyboard
 * Contains paste last transcription button (with long press for history) and space button
 * Double-tapping space will insert a period followed by a space
 */
@Composable
fun BottomActionsRow(
    lastTranscribedText: String,
    transcriptionHistory: List<TranscriptionHistoryItem>,
    enableHistoryPanel: Boolean,
    onPasteText: (String) -> Unit,
    onShowHistory: () -> Unit,
    onSpace: () -> Unit,
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    // Track last space press time for double-space to period (500ms threshold)
    var lastSpacePressTime by remember { mutableStateOf(0L) }

    val handleSpacePress = {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastSpace = currentTime - lastSpacePressTime

        if (timeSinceLastSpace < 500L && lastSpacePressTime > 0L) {
            // Double-space detected: delete the previous space and insert period + space
            onDelete() // Remove the previous space
            onPasteText(". ") // Insert period and space
            lastSpacePressTime = 0L // Reset to prevent triple-space issues
        } else {
            // Normal space press
            onSpace()
            lastSpacePressTime = currentTime
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Paste last transcribed text button with long press for history
        // Show if there's last transcribed text OR if there's history available
        if (lastTranscribedText.isNotEmpty() || transcriptionHistory.isNotEmpty()) {
            // Use lastTranscribedText if available, otherwise use first history item
            val textToShow = if (lastTranscribedText.isNotEmpty()) lastTranscribedText else transcriptionHistory.first().text

            Surface(
                modifier = Modifier
                    .weight(1.3f)
                    .height(56.dp)
                    .pointerInput(enableHistoryPanel) {
                        detectTapGestures(
                            onTap = { onPasteText(textToShow) },
                            onLongPress = {
                                if (enableHistoryPanel) {
                                    onShowHistory()
                                }
                            }
                        )
                    },
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = strings.pasteLastTranscription,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            strings.pasteLastHold.uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            if (textToShow.length > 40) {
                                textToShow.take(40) + "..."
                            } else {
                                textToShow
                            },
                            fontSize = 8.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Space button (minimal elongated bar like a space bar)
        // Double-tap to insert period and space
        Button(
            onClick = handleSpacePress,
            modifier = Modifier
                .weight(if (lastTranscribedText.isEmpty() && transcriptionHistory.isEmpty()) 1f else 0.6f)
                .height(56.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = strings.space,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFeatureSettings = "smcp" // Small caps
                )
            )
        }
    }
}
