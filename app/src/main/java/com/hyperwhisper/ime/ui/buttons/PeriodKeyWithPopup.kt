package com.hyperwhisper.ui.buttons

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
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
 * Gboard-style long-press punctuation selector for the period key — now a
 * **multi-row grid** (the user asked for "a couple of rows for dot") with the
 * same single-gesture, edge-aware model as [AccentKeyWithPopup]:
 *
 *  - tap-and-release types "." (the rest cell, bottom row);
 *  - press-and-hold opens the grid above the key;
 *  - without lifting, drag X across columns and Y across rows to highlight;
 *  - release commits the highlighted character.
 *
 * Horizontal placement is clamped to the viewport via [PopupPlacement] so the
 * grid never spills off-screen for edge keys; rows stack upward with the rest
 * row nearest the key.
 */
@Composable
fun PeriodKeyWithPopup(
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    bg: Color = KeyboardKeyColor,
    fg: Color = KeyboardKeyTextColor,
    longPressMs: Long = 300L,
    rows: List<List<String>> = DEFAULT_PERIOD_GRID,
) {
    val cols = rows.maxOf { it.size }
    // Rest cell = the "." in the bottom row (or grid center if absent), so a
    // plain press-release stays a literal period.
    val restRow = rows.indexOfLast { it.contains(".") }.let { if (it < 0) rows.lastIndex else it }
    val restCol = rows[restRow].indexOf(".").let { if (it < 0) cols / 2 else it }

    val density = LocalDensity.current
    val touchSlopPx = with(density) { 16.dp.toPx() }
    val spacingPx = with(density) { 2.dp.toPx() }
    val paddingPx = with(density) { 2.dp.toPx() }
    val cellHPx = with(density) { height.toPx() }
    val rowStridePx = cellHPx + spacingPx
    val gapPx = with(density) { 6.dp.toPx() }
    val gridHeightPx = paddingPx * 2 + rows.size * cellHPx + (rows.size - 1) * spacingPx
    // IME spans the full screen width → use it as the clamp viewport.
    val viewportWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }.roundToInt()

    val scope = rememberCoroutineScope()
    var popupVisible by remember { mutableStateOf(false) }
    var hiRow by remember { mutableIntStateOf(restRow) }
    var hiCol by remember { mutableIntStateOf(restCol) }
    var keyWidthPx by remember { mutableIntStateOf(0) }
    var keyLeftPx by remember { mutableFloatStateOf(0f) }
    var keyTopPx by remember { mutableFloatStateOf(0f) }

    fun stripLeft(): Float = PopupPlacement.stripLeftPx(
        keyLeftPx = keyLeftPx,
        keyWidthPx = keyWidthPx,
        viewportWidthPx = viewportWidthPx,
        cellCount = cols,
        cellWidthPx = keyWidthPx,
        spacingPx = spacingPx,
        paddingPx = paddingPx,
        restIndex = restCol,
    )

    // Top of the grid in window coords (grid sits above the key, rest row nearest).
    fun gridTop(): Float = keyTopPx - gapPx - gridHeightPx

    fun safe(r: Int): List<String> = rows[r]
    fun charAt(r: Int, c: Int): String = safe(r).getOrElse(c.coerceIn(0, safe(r).lastIndex)) { "." }

    Box(
        modifier = modifier
            .height(height)
            .onGloballyPositioned { coords ->
                keyWidthPx = coords.size.width
                keyLeftPx = coords.positionInWindow().x
                keyTopPx = coords.positionInWindow().y
            }
            .pointerInput(rows) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    var holdJob: Job? = scope.launch {
                        delay(longPressMs)
                        hiRow = restRow
                        hiCol = restCol
                        popupVisible = true
                    }

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (popupVisible) {
                            val pointerXInWindow = keyLeftPx + change.position.x
                            val pointerYInWindow = keyTopPx + change.position.y
                            hiCol = PopupPlacement.cellIndexAt(
                                pointerXPx = pointerXInWindow,
                                stripLeftPx = stripLeft(),
                                cellCount = cols,
                                cellWidthPx = keyWidthPx,
                                spacingPx = spacingPx,
                                paddingPx = paddingPx,
                            )
                            // Rows stack upward; clamp keeps the finger-on-key
                            // position on the bottom (rest) row.
                            hiRow = (((pointerYInWindow - (gridTop() + paddingPx)) / rowStridePx)
                                .toInt()).coerceIn(0, rows.lastIndex)
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
                                onKeyPress(charAt(hiRow, hiCol))
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
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(8.dp),
            color = bg,
            tonalElevation = 1.dp
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = ".", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = fg)
            }
        }

        if (popupVisible && keyWidthPx > 0) {
            val xOffsetPx = (stripLeft() - keyLeftPx).roundToInt()
            val yOffsetPx = -(gapPx + gridHeightPx).roundToInt()
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
                    Column(
                        modifier = Modifier.padding(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        rows.forEachIndexed { r, rowChars ->
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                rowChars.forEachIndexed { c, ch ->
                                    val highlighted = r == hiRow && c == hiCol
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
                                                text = ch,
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
    }
}

// Two-row punctuation grid. Bottom row carries "." (the rest cell) so a plain
// tap-release types a period; common sentence + chat punctuation fills the rest.
private val DEFAULT_PERIOD_GRID: List<List<String>> = listOf(
    listOf("?", "!", ":", ";", "—", "…"),
    listOf(",", "'", "\"", ".", "/", "@"),
)
