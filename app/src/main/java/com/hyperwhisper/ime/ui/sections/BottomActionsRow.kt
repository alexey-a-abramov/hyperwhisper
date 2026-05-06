package com.hyperwhisper.ui.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hyperwhisper.data.TranscriptionHistoryItem

/**
 * Dictation-mode bottom row. Thin wrapper around [KeyboardBottomBar] kept
 * for naming continuity at the call site — every other layout calls the
 * shared bar directly.
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
    KeyboardBottomBar(
        lastTranscribedText = lastTranscribedText,
        transcriptionHistory = transcriptionHistory,
        enableHistoryPanel = enableHistoryPanel,
        onPasteText = onPasteText,
        onShowHistory = onShowHistory,
        onSpace = onSpace,
        onEnter = onEnter,
        modifier = modifier
    )
}
