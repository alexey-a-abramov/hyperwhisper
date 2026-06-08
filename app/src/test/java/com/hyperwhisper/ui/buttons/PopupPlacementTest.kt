package com.hyperwhisper.ui.buttons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PopupPlacement] — the edge-aware geometry that keeps a
 * long-press char strip fully on-screen (the fix for "holding a left-edge key
 * hid the left-side accents").
 */
class PopupPlacementTest {

    // 5 cells × 100px, no spacing/padding, 1000px viewport → strip is 500 wide.
    private fun left(keyLeftPx: Float, restIndex: Int = 2) = PopupPlacement.stripLeftPx(
        keyLeftPx = keyLeftPx,
        keyWidthPx = 100,
        viewportWidthPx = 1000,
        cellCount = 5,
        cellWidthPx = 100,
        spacingPx = 0f,
        paddingPx = 0f,
        restIndex = restIndex,
    )

    @Test
    fun centeredKey_centersStripOverKey() {
        // key at [450,550], center 500; base (idx2) center should sit at 500.
        val stripLeft = left(keyLeftPx = 450f)
        assertEquals(250f, stripLeft, 0.5f)
    }

    @Test
    fun leftEdgeKey_pinsStripToLeft_soLeftVariantsStayReachable() {
        // Holding the left-edge key must not push cells off-screen.
        val stripLeft = left(keyLeftPx = 0f)
        assertEquals(0f, stripLeft, 0.5f)
    }

    @Test
    fun rightEdgeKey_pinsStripToRightEdge() {
        // key at far right; strip clamps so its right edge == viewport.
        val stripLeft = left(keyLeftPx = 900f)
        assertEquals(500f, stripLeft, 0.5f) // 1000 - 500
        assertTrue(stripLeft + 500f <= 1000f)
    }

    @Test
    fun stripWiderThanViewport_pinsLeft() {
        val stripLeft = PopupPlacement.stripLeftPx(
            keyLeftPx = 100f, keyWidthPx = 100, viewportWidthPx = 300,
            cellCount = 9, cellWidthPx = 100, spacingPx = 0f, paddingPx = 0f, restIndex = 4,
        )
        assertEquals(0f, stripLeft, 0.5f)
    }

    @Test
    fun cellIndex_mapsPointerToCell() {
        assertEquals(0, PopupPlacement.cellIndexAt(50f, 0f, 5, 100, 0f, 0f))
        assertEquals(2, PopupPlacement.cellIndexAt(250f, 0f, 5, 100, 0f, 0f))
        assertEquals(4, PopupPlacement.cellIndexAt(480f, 0f, 5, 100, 0f, 0f))
    }

    @Test
    fun cellIndex_clampsOutOfRange() {
        assertEquals(0, PopupPlacement.cellIndexAt(-20f, 0f, 5, 100, 0f, 0f))
        assertEquals(4, PopupPlacement.cellIndexAt(9999f, 0f, 5, 100, 0f, 0f))
    }

    @Test
    fun cellIndex_accountsForStripOffset() {
        // strip starts at 500 → a pointer at 550 is over cell 0.
        assertEquals(0, PopupPlacement.cellIndexAt(550f, 500f, 5, 100, 0f, 0f))
        assertEquals(3, PopupPlacement.cellIndexAt(850f, 500f, 5, 100, 0f, 0f))
    }
}
