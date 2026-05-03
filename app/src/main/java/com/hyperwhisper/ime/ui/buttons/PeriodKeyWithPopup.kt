package com.hyperwhisper.ui.buttons

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Gboard-style long-press char selector for the period key.
 *
 * Single continuous gesture: press the period, hold for [longPressMs], a popup
 * row appears centered over the key. Without lifting, drag left/right to
 * highlight a character; release commits it. A short tap (no hold) types ".".
 *
 * The chars list defaults to a standard punctuation set with "." in the middle
 * so the natural "press and release" gesture stays a literal period.
 */
@Composable
fun PeriodKeyWithPopup(
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    bg: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    fg: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    longPressMs: Long = 300L,
    chars: List<String> = DEFAULT_PERIOD_CHARS,
) {
    val periodIndex = chars.indexOf(".").let { if (it < 0) chars.size / 2 else it }
    val density = LocalDensity.current
    val touchSlopPx = with(density) { 16.dp.toPx() }
    val popupCellHeightPx = with(density) { (height + 8.dp).toPx() }

    val scope = rememberCoroutineScope()
    var popupVisible by remember { mutableStateOf(false) }
    var highlightedIndex by remember { mutableIntStateOf(periodIndex) }
    var keyWidthPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .height(height)
            .onSizeChanged { keyWidthPx = it.width }
            .pointerInput(chars) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    var holdJob: Job? = scope.launch {
                        delay(longPressMs)
                        highlightedIndex = periodIndex
                        popupVisible = true
                    }

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (popupVisible) {
                            // Map pointer x (local to the period key) to a popup
                            // cell. Pointer at the key's horizontal center stays
                            // on "." (periodIndex); each cell-width offset shifts
                            // selection by one slot.
                            val xFromCenter = change.position.x - keyWidthPx / 2f
                            val cellOffset = if (keyWidthPx > 0) {
                                (xFromCenter / keyWidthPx).roundToInt()
                            } else 0
                            highlightedIndex = (periodIndex + cellOffset)
                                .coerceIn(0, chars.size - 1)
                        } else {
                            // Pre-popup: if the pointer wandered too far, cancel
                            // the long-press timer and let the user drag away.
                            val dx = change.position.x - downPos.x
                            val dy = change.position.y - downPos.y
                            if (dx * dx + dy * dy > touchSlopPx * touchSlopPx) {
                                holdJob?.cancel()
                                holdJob = null
                            }
                        }

                        if (change.changedToUp()) {
                            holdJob?.cancel()
                            if (popupVisible) {
                                onKeyPress(chars[highlightedIndex])
                                popupVisible = false
                            } else {
                                onKeyPress(".")
                            }
                            break
                        }
                    }
                }
            }
    ) {
        // Base period key — same look as KeyboardKeyButton.
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(8.dp),
            color = bg,
            tonalElevation = 1.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ".",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = fg
                )
            }
        }

        if (popupVisible && keyWidthPx > 0) {
            // Anchor the popup so the period cell is centered above the key.
            val xOffsetPx = -periodIndex * keyWidthPx
            val yOffsetPx = -popupCellHeightPx.toInt()
            Popup(
                offset = IntOffset(xOffsetPx, yOffsetPx),
                properties = PopupProperties(focusable = false),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        chars.forEachIndexed { i, c ->
                            val highlighted = i == highlightedIndex
                            Surface(
                                modifier = Modifier
                                    .width(with(density) { keyWidthPx.toDp() })
                                    .height(height),
                                shape = RoundedCornerShape(6.dp),
                                color = if (highlighted)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = c,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (highlighted)
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Gboard-style English period popup: comma + sentence-enders + common
// programmer punctuation, with "." centered so a plain tap-and-release still
// types a literal period.
private val DEFAULT_PERIOD_CHARS = listOf(
    ",", "#", "!", "?", "-", ".", "/", "@", "'", "\"", ";"
)
