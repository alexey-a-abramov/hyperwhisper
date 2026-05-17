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
import androidx.compose.material.icons.filled.Backspace
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
import com.hyperwhisper.ui.util.repeatOnHold

/**
 * Dictation-only top header. Replaces [UniversalKeyboardTopStrip] when the
 * keyboard is in voice mode.
 *
 * Why a separate composable: voice mode wants a different chrome layout —
 * mode chips on the left, gear + backspace stacked on the right — whereas
 * every other layout uses the single-row universal strip with Esc / Tab /
 * Backspace inline. Forking the chrome avoids polluting the universal strip
 * with mode-specific branches.
 *
 * ```
 *  [Voice] [A] [Preset]          [⚙️]
 *                                [⌫]
 * ```
 *
 * Mode chips stay in the same screen positions as the universal strip so
 * cycling between voice and a typing layout doesn't move the eye. The
 * backspace uses the same rectangular "action chip" styling as the universal
 * strip's backspace (red errorContainer background, KeyboardMetrics.KeyRadius
 * corners, repeat-on-hold) so the design is unified across layouts.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DictationHeader(
    currentMode: KeyboardInputMode,
    presetMode: KeyboardInputMode,
    onSelectMode: (KeyboardInputMode) -> Unit,
    onPresetLongPress: () -> Unit,
    onBackspace: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val normalized = currentMode.normalize()
    val normalizedPreset = presetMode.normalize().let {
        // Voice + QWERTY have dedicated slots; preset can't shadow them.
        if (it == KeyboardInputMode.DICTATION || it == KeyboardInputMode.QWERTY)
            KeyboardInputMode.CODE else it
    }
    // Header is tall enough to stack gear over backspace on the right column.
    // Each chip in the column is the same height as a normal top-strip chip,
    // separated by RowGap.
    val chipHeight = KeyboardMetrics.TopStripHeight
    val headerHeight = chipHeight * 2 + KeyboardMetrics.RowGap

    Surface(
        modifier = modifier.fillMaxWidth().height(headerHeight),
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
            // Top-aligned: mode chips on the left sit on the same baseline as
            // the universal top strip in every other layout. The right column
            // (gear over backspace) is taller and fills below — gear stays
            // visually adjacent to the mode chips in the top row.
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(KeyboardMetrics.TopStripKeyGap)
        ) {
            // --- Mode chips, vertically centered in the taller header ---
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
                    .padding(vertical = KeyboardMetrics.RowGap)
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

            // --- Right column: gear stacked over backspace ---
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(KeyboardMetrics.RowGap)
            ) {
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
                // Backspace — rectangular, red errorContainer, matches the
                // universal top strip's backspace chip. Repeat-on-hold so a
                // long press deletes word-by-word, same as everywhere else.
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
                    modifier = Modifier
                        .height(chipHeight)
                        .width(KeyboardMetrics.TopStripIconWidth)
                        .repeatOnHold(onTrigger = onBackspace)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Backspace,
                            contentDescription = strings.keyboardBackspaceDesc,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Small dictation-action chip — used for the inline Esc / Tab buttons that
 * sit directly under the mic in voice mode. Same shape and colors as the
 * universal top strip's chip buttons so the design reads as one family.
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
        modifier = modifier.height(KeyboardMetrics.TopStripHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = KeyboardMetrics.BaseUnit * 4),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
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
            .padding(vertical = KeyboardMetrics.RowGap)
            .width(KeyboardMetrics.TopStripIconWidth)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}
