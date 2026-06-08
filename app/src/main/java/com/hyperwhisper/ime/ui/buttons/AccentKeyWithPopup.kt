package com.hyperwhisper.ui.buttons

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.hyperwhisper.ui.KeyboardKeyColor
import com.hyperwhisper.ui.KeyboardKeyTextColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Letter key with a Gboard-style long-press accent selector.
 *
 * Tap-and-release types [baseChar]; press-and-hold opens a popup strip of
 * accent variants, then drag left/right to highlight and release to commit.
 * The strip is **edge-aware** ([PopupPlacement]): it tries to center the base
 * letter over the key but clamps to the viewport so variants never fall off
 * the screen — the fix for left/right-edge keys whose variants used to be
 * unreachable.
 *
 * [accents] are expected pre-cased by the caller (the QWERTY section applies
 * shift/caps), so this component is case-agnostic.
 */
@Composable
internal fun AccentKeyWithPopup(
    baseChar: String,
    accents: List<String>,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    longPressMs: Long = 300L,
) {
    // Base letter centered so a plain tap-release lands on it; the edge-aware
    // placement then shifts the whole strip on-screen as needed.
    val capped = accents.take(8)
    val half = capped.size / 2
    val chars = capped.take(half) + listOf(baseChar) + capped.drop(half)
    val restIndex = half

    val density = LocalDensity.current
    val touchSlopPx = with(density) { 16.dp.toPx() }
    val spacingPx = with(density) { 2.dp.toPx() }
    val paddingPx = with(density) { 2.dp.toPx() }
    val yGapPx = with(density) { (height + 6.dp).toPx() }
    // IME spans the full screen width, so the screen width is the viewport we
    // clamp the popup strip into.
    val viewportWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }.roundToInt()

    val scope = rememberCoroutineScope()
    var popupVisible by remember { mutableStateOf(false) }
    var highlightedIndex by remember { mutableIntStateOf(restIndex) }
    var keyWidthPx by remember { mutableIntStateOf(0) }
    var keyLeftPx by remember { mutableFloatStateOf(0f) }

    fun stripLeft(): Float = PopupPlacement.stripLeftPx(
        keyLeftPx = keyLeftPx,
        keyWidthPx = keyWidthPx,
        viewportWidthPx = viewportWidthPx,
        cellCount = chars.size,
        cellWidthPx = keyWidthPx,
        spacingPx = spacingPx,
        paddingPx = paddingPx,
        restIndex = restIndex,
    )

    Box(
        modifier = modifier
            .height(height)
            .onGloballyPositioned { coords ->
                keyWidthPx = coords.size.width
                keyLeftPx = coords.positionInWindow().x
            }
            .pointerInput(chars) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    var holdJob: Job? = scope.launch {
                        delay(longPressMs)
                        highlightedIndex = restIndex
                        popupVisible = true
                    }

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (popupVisible) {
                            // Map the pointer's absolute x to a cell using the
                            // strip's clamped on-screen position.
                            val pointerXInWindow = keyLeftPx + change.position.x
                            highlightedIndex = PopupPlacement.cellIndexAt(
                                pointerXPx = pointerXInWindow,
                                stripLeftPx = stripLeft(),
                                cellCount = chars.size,
                                cellWidthPx = keyWidthPx,
                                spacingPx = spacingPx,
                                paddingPx = paddingPx,
                            )
                        } else {
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
                                onKeyPress(baseChar)
                            }
                            break
                        }
                    }
                }
            }
    ) {
        // Base key — same look as KeyboardKeyButton.
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(8.dp),
            color = KeyboardKeyColor,
            tonalElevation = 1.dp
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = baseChar,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = KeyboardKeyTextColor
                )
            }
        }

        if (popupVisible && keyWidthPx > 0) {
            // Popup offset is relative to this key's window position; shift it
            // to the clamped strip-left.
            val xOffsetPx = (stripLeft() - keyLeftPx).roundToInt()
            Popup(
                offset = IntOffset(xOffsetPx, -yGapPx.roundToInt()),
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
