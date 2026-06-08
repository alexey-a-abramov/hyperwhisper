package com.hyperwhisper.ui.buttons

/**
 * Pure geometry for long-press character popups (accents, punctuation).
 *
 * The popup is a horizontal strip of equal-width cells shown above the pressed
 * key. Naively centering the strip over the key pushes cells off-screen for
 * edge keys (e.g. holding "a" at the left edge hid à/á/â/ä), so the strip is
 * clamped to the viewport and the pointer→cell mapping uses the strip's actual
 * on-screen position. Kept dependency-free so it's unit-tested directly.
 */
internal object PopupPlacement {

    /**
     * Left edge (in root/window px) for a strip of [cellCount] cells, trying to
     * center the [restIndex] cell over the key (left [keyLeftPx], width
     * [keyWidthPx]) but clamping so the whole strip stays within
     * [viewportWidthPx]. [cellWidthPx] is each cell's width; [spacingPx]
     * separates cells; [paddingPx] pads both ends of the strip.
     */
    fun stripLeftPx(
        keyLeftPx: Float,
        keyWidthPx: Int,
        viewportWidthPx: Int,
        cellCount: Int,
        cellWidthPx: Int,
        spacingPx: Float,
        paddingPx: Float,
        restIndex: Int,
    ): Float {
        val stride = cellWidthPx + spacingPx
        val contentWidth = paddingPx * 2 + cellCount * cellWidthPx + (cellCount - 1) * spacingPx
        val keyCenter = keyLeftPx + keyWidthPx / 2f
        val restCellCenter = paddingPx + restIndex * stride + cellWidthPx / 2f
        val desired = keyCenter - restCellCenter
        // If the strip is wider than the viewport there's nothing to clamp to;
        // pin to the left edge so at least the leading cells are reachable.
        val maxLeft = (viewportWidthPx - contentWidth).coerceAtLeast(0f)
        return desired.coerceIn(0f, maxLeft)
    }

    /**
     * Index of the cell the pointer at [pointerXPx] (root/window px) sits over,
     * given the strip's [stripLeftPx]. Clamped to `0 until cellCount`.
     */
    fun cellIndexAt(
        pointerXPx: Float,
        stripLeftPx: Float,
        cellCount: Int,
        cellWidthPx: Int,
        spacingPx: Float,
        paddingPx: Float,
    ): Int {
        val stride = cellWidthPx + spacingPx
        val rel = pointerXPx - stripLeftPx - paddingPx
        return (rel / stride).toInt().coerceIn(0, cellCount - 1)
    }
}
