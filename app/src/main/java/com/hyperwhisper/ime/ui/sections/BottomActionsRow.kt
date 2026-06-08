package com.hyperwhisper.ui.sections

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.TranscriptionHistoryItem
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.KeyboardBackspaceColor
import com.hyperwhisper.ui.KeyboardEnterColor
import com.hyperwhisper.ui.KeyboardMetrics
import com.hyperwhisper.ui.buttons.LocalityKey
import com.hyperwhisper.ui.buttons.PeriodKeyWithPopup
import com.hyperwhisper.ui.components.LongPressIndicator
import com.hyperwhisper.ui.util.repeatOnHold

/**
 * Dictation-mode bottom action area. Two-row tall: the right column stacks
 * Backspace over Enter, while the left side runs Paste / `,` / Space / `.`
 * along the bottom only.
 *
 * ```
 *                                          ┌─────┐
 *                                          │  ⌫  │
 *  [Paste] [,] [   Space   ] [.]           ├─────┤
 *                                          │  ⏎  │
 *                                          └─────┘
 * ```
 *
 * The dot key is a [PeriodKeyWithPopup] — the same hold-to-grid punctuation
 * selector used on the QWERTY row — so the long-press behaviour and key width
 * are identical across the dictation and typing keyboards.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomActionsRow(
    lastTranscribedText: String,
    transcriptionHistory: List<TranscriptionHistoryItem>,
    enableHistoryPanel: Boolean,
    localityCode: String,
    onCommitText: (String) -> Unit,
    onShowHistory: () -> Unit,
    onCycleLocality: () -> Unit,
    onShowLocalityList: () -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val rowHeight = KeyboardMetrics.BottomBarHeight
    val charKeyWidth = KeyboardMetrics.PunctKeyWidth
    val outerHeight = rowHeight * 2 + KeyboardMetrics.BottomBarSpacing

    Row(
        modifier = modifier.fillMaxWidth().height(outerHeight),
        horizontalArrangement = Arrangement.spacedBy(KeyboardMetrics.BottomBarSpacing),
        verticalAlignment = Alignment.Bottom
    ) {
        // Left side: paste / , / space / .  along the bottom only.
        Row(
            modifier = Modifier.weight(1f).height(rowHeight),
            horizontalArrangement = Arrangement.spacedBy(KeyboardMetrics.BottomBarSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Paste shrinks (weight 1f → was 1.4f) to free space for the
            // new comma/dot keys.
            PasteLastPill(
                lastTranscribedText = lastTranscribedText,
                transcriptionHistory = transcriptionHistory,
                enableHistoryPanel = enableHistoryPanel,
                onPasteText = onCommitText,
                onShowHistory = onShowHistory,
                weight = 1f
            )
            // Locality switcher — tap cycles the enabled keyboard localities
            // (and points dictation at that language); long-press opens the
            // full list. Sits between Paste and the comma key.
            LocalityKey(
                code = localityCode,
                onClick = onCycleLocality,
                onLongClick = onShowLocalityList,
                modifier = Modifier.width(charKeyWidth).fillMaxHeight()
            )
            CharKey(
                label = ",",
                onClick = { onCommitText(",") },
                modifier = Modifier.width(charKeyWidth).fillMaxHeight()
            )
            BottomBarSpace(
                // Smaller weight than before — still the dominant target but
                // not the full width-eater. Drops further (1f → 0.8f) when
                // the paste pill is hidden so the row stays balanced.
                weight = if (hasPasteContent(lastTranscribedText, transcriptionHistory)) 1.2f else 0.8f,
                onClick = onSpace
            )
            // Period — shared hold-to-grid punctuation popup (same component
            // and width as the QWERTY row's period key).
            PeriodKeyWithPopup(
                onKeyPress = onCommitText,
                bg = MaterialTheme.colorScheme.surfaceVariant,
                fg = MaterialTheme.colorScheme.onSurfaceVariant,
                height = rowHeight,
                modifier = Modifier.width(charKeyWidth).fillMaxHeight()
            )
        }

        // Right column: backspace stacked above enter. Same fixed
        // EnterKeyWidth as every other layout so the enter screen position
        // stays consistent; backspace inherits that width to sit above it
        // cleanly.
        Column(
            modifier = Modifier
                .width(KeyboardMetrics.EnterKeyWidth)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(KeyboardMetrics.BottomBarSpacing)
        ) {
            Surface(
                color = KeyboardBackspaceColor,
                contentColor = Color.White,
                shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .repeatOnHold(onTrigger = onDelete)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = strings.keyboardBackspaceDesc,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            // Enter — canonical green (same KeyboardEnterColor as the typing
            // layouts) so the "submit" target reads the same in every mode.
            Surface(
                onClick = onEnter,
                color = KeyboardEnterColor,
                contentColor = Color.White,
                shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.KeyboardReturn,
                        contentDescription = strings.enterDesc,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CharKey(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    showLongPressDot: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (showLongPressDot) LongPressIndicator(padding = 3.dp)
    }
}
