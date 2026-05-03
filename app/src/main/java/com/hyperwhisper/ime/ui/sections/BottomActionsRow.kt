package com.hyperwhisper.ui.sections

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.hyperwhisper.data.TranscriptionHistoryItem
import com.hyperwhisper.localization.LocalStrings

private val KeyboardModeSwitcherColor = Color(0xFF424242)

@Composable
private fun UnifiedModeSwitcherCompact(
    currentMode: KeyboardInputMode,
    onModeChange: (KeyboardInputMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ABC button
        Surface(
            onClick = {
                when (currentMode) {
                    KeyboardInputMode.QWERTY -> onModeChange(KeyboardInputMode.SPECIAL_CHARS)
                    KeyboardInputMode.SPECIAL_CHARS -> onModeChange(KeyboardInputMode.QWERTY)
                    else -> onModeChange(KeyboardInputMode.QWERTY)
                }
            },
            modifier = Modifier.weight(1f).height(56.dp),
            shape = RoundedCornerShape(8.dp),
            color = if (currentMode == KeyboardInputMode.QWERTY || currentMode == KeyboardInputMode.SPECIAL_CHARS)
                MaterialTheme.colorScheme.primary else KeyboardModeSwitcherColor
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ABC",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Dictation button (current mode - shown but not clickable since we're already in it)
        Surface(
            modifier = Modifier.weight(1f).height(56.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary // Always highlighted in dictation mode
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Dictation",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Vibe Coding button
        Surface(
            onClick = { onModeChange(KeyboardInputMode.VIBE_CODING) },
            modifier = Modifier.weight(1f).height(56.dp),
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

/**
 * Bottom actions row of the keyboard
 * Contains mode switcher, paste last transcription button (with long press for history) and space button
 */
@Composable
fun BottomActionsRow(
    lastTranscribedText: String,
    transcriptionHistory: List<TranscriptionHistoryItem>,
    enableHistoryPanel: Boolean,
    onPasteText: (String) -> Unit,
    onShowHistory: () -> Unit,
    onSpace: () -> Unit,
    showKeyboardButton: Boolean = false,
    onKeyboardButtonClick: () -> Unit = {},
    currentKeyboardMode: KeyboardInputMode = KeyboardInputMode.DICTATION,
    onModeChange: (KeyboardInputMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Unified mode switcher (replaces single keyboard button)
        if (showKeyboardButton) {
            UnifiedModeSwitcherCompact(
                currentMode = currentKeyboardMode,
                onModeChange = onModeChange,
                modifier = Modifier.width(180.dp)
            )
        }

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

        // Space — same canonical yellow as every other layout's spacebar.
        Button(
            onClick = onSpace,
            modifier = Modifier
                .weight(
                    if (lastTranscribedText.isEmpty() && transcriptionHistory.isEmpty()) 1f
                    else 0.6f
                )
                .height(56.dp),
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
    }
}
