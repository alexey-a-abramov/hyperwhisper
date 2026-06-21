package com.hyperwhisper.ui.sections

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
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
import com.hyperwhisper.data.KeyboardInputMode
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.KeyboardMetrics
import com.hyperwhisper.ui.components.LongPressIndicator
import com.hyperwhisper.ui.util.localizedDisplayName

/**
 * Dictation-only top header. Replaces [UniversalKeyboardTopStrip] when the
 * keyboard is in voice mode.
 *
 * Layout:
 *
 * ```
 *  [🎤] [A] [Preset]                  [⚙️]
 * ```
 *
 * Mode chips stay in the same screen positions as the universal strip so
 * cycling between voice and a typing layout doesn't move the eye. Esc and Tab
 * used to live below this row but have been moved into the recording area's
 * lower-left column so the header collapses to a single row and the mic gets
 * more vertical breathing room. Backspace lives stacked above Enter in the
 * bottom action row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DictationHeader(
    currentMode: KeyboardInputMode,
    presetMode: KeyboardInputMode,
    onSelectMode: (KeyboardInputMode) -> Unit,
    onPresetLongPress: () -> Unit,
    onSettings: () -> Unit,
    pasteText: String = "",
    enableHistoryPanel: Boolean = false,
    onPaste: (String) -> Unit = {},
    onShowHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val normalized = currentMode.normalize()
    val normalizedPreset = presetMode.normalize().let {
        // Voice + QWERTY have dedicated slots; preset can't shadow them.
        if (it == KeyboardInputMode.DICTATION || it == KeyboardInputMode.QWERTY)
            KeyboardInputMode.CODE else it
    }

    val chipHeight = KeyboardMetrics.TopStripHeight

    Surface(
        modifier = modifier.fillMaxWidth().height(chipHeight + KeyboardMetrics.RowGap * 2),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = KeyboardMetrics.OuterPadding,
                    vertical = KeyboardMetrics.RowGap
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KeyboardMetrics.TopStripKeyGap)
        ) {
            ModeChip(
                isSelected = normalized == KeyboardInputMode.DICTATION,
                onClick = { onSelectMode(KeyboardInputMode.DICTATION) }
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = strings.keyboardBackToVoiceDesc,
                    modifier = Modifier.size(16.dp)
                )
            }
            ModeChip(
                isSelected = normalized == KeyboardInputMode.QWERTY,
                onClick = { onSelectMode(KeyboardInputMode.QWERTY) }
            ) {
                Text("A", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            val presetIsCurrent = normalized == normalizedPreset
            Box(
                modifier = Modifier
                    .height(chipHeight)
                    .width(KeyboardMetrics.ModeChipWidth)
            ) {
                Surface(
                    color = if (presetIsCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (presetIsCurrent) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
                    modifier = Modifier
                        .fillMaxSize()
                        .combinedClickable(
                            onClick = { onSelectMode(normalizedPreset) },
                            onLongClick = onPresetLongPress
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = normalizedPreset.localizedDisplayName(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
                LongPressIndicator(padding = 3.dp)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Insert (paste-last) — relocated here from the dictation bottom
            // row. Tap pastes the last transcription, long-press opens history.
            InsertChip(
                pasteText = pasteText,
                enableHistoryPanel = enableHistoryPanel,
                onPaste = onPaste,
                onShowHistory = onShowHistory,
                modifier = Modifier
                    .height(chipHeight)
                    .width(KeyboardMetrics.ModeChipWidth)
            )

            Surface(
                onClick = onSettings,
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
                modifier = Modifier
                    .height(chipHeight)
                    .width(KeyboardMetrics.TopStripIconWidth)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = strings.settingsDesc,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Small dictation-action chip — same shape and colors as the universal top
 * strip's chip buttons so the design reads as one family. Caller controls
 * width/height via the passed [modifier]; pass a sized modifier or the chip
 * will collapse to its content.
 */
@Composable
fun DictationActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = KeyboardMetrics.BaseUnit * 4),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ModeChip(
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
        modifier = Modifier
            .height(KeyboardMetrics.TopStripHeight)
            .width(KeyboardMetrics.TopStripIconWidth)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}
