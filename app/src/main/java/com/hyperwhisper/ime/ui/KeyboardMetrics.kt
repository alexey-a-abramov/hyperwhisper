package com.hyperwhisper.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Single source of truth for keyboard sizing.
 *
 * Two anchor values drive everything else:
 *  - [BoardHeight] — total IME content height. Every *vertical* dimension is
 *    a fraction of it, so the whole keyboard scales as one unit when the IME
 *    grows (landscape, larger-keyboard setting, accessibility scaling).
 *  - [BaseUnit] — a 4dp design-token grid. Every *horizontal/spacing/radius*
 *    dimension is a whole or half multiple, since columns already flex via
 *    Modifier.weight and don't need to track [BoardHeight].
 *
 * Ergonomically-pinned widths (Enter, mode chip, top-strip icons) are *not*
 * derived: they're driven by minimum touch targets and target-string widths
 * that don't scale with overall board height.
 *
 * Layouts read named metrics from here rather than inlining .dp literals.
 * If a value needs to change app-wide, it changes here.
 */
object KeyboardMetrics {
    // ----- Anchors -----------------------------------------------------------
    /** Total IME content height. Matches the Box height in KeyboardScreen. */
    val BoardHeight: Dp = 320.dp

    /** Smallest design-token unit. Every horizontal padding / spacing / radius
     *  below is a half or whole multiple of this. */
    val BaseUnit: Dp = 4.dp

    // ----- Vertical (fraction of BoardHeight) --------------------------------
    /** Universal top strip across every layout — mode chips + Esc/Tab/Bksp +
     *  Settings/Logs. Sized to a comfortable 44dp icon target at 320dp board. */
    val TopStripHeight: Dp = BoardHeight * 0.125f          // 40dp

    /** Universal bottom bar — paste pill + space + enter. */
    val BottomBarHeight: Dp = BoardHeight * 0.1375f        // 44dp

    /** Single-character punctuation key width (comma, period) — shared by the
     *  dictation row and the QWERTY row so the two read identically. */
    val PunctKeyWidth: Dp = BoardHeight * 0.1375f          // 44dp

    /** Padding around the typing-area surface, applied on all four sides. */
    val OuterPadding: Dp = BoardHeight * 0.0125f           // 4dp

    /** Spacing between rows inside a typing-area Column. */
    val RowGap: Dp = BoardHeight * 0.00625f                // 2dp

    /** Key height for spacious 4-row layouts (QWERTY letters). Also the
     *  ceiling used when [TextKeyboardSection] clamps its derived keyHeight. */
    val KeyHeightStandard: Dp = BoardHeight * 0.140625f    // 45dp

    /** Key height for dense 6-row layouts (Code, Agent inline row). */
    val KeyHeightCompact: Dp = BoardHeight * 0.1125f       // 36dp

    /** Lower clamp for layouts that derive their key height from available
     *  space (QWERTY's [BoxWithConstraints]). Prevents sub-ergonomic targets
     *  on unusually small boards. */
    val KeyHeightFloor: Dp = BoardHeight * 0.10f           // 32dp

    /** Ceiling for the same derivation. Keys taller than this look comical
     *  and waste vertical space that could go to the bottom bar or chrome. */
    val KeyHeightCeiling: Dp = BoardHeight * 0.15f         // 48dp

    // ----- Horizontal / spacing / radii (multiple of BaseUnit) ---------------
    /** Spacing between adjacent keys in a typing row. Tight, since keys are
     *  visually separated by their surface color. */
    val KeySpacing: Dp = BaseUnit * 0.5f                   // 2dp

    /** Corner radius on individual keys. */
    val KeyRadius: Dp = BaseUnit * 2f                      // 8dp

    /** Corner radius on the outer keyboard surface (rounds the whole panel). */
    val SurfaceRadius: Dp = BaseUnit * 2.5f                // 10dp

    /** Internal padding on key content (icon/text insets from surface edge). */
    val KeyContentPadding: Dp = BaseUnit                   // 4dp

    /** Spacing between top-strip chips — wider than [KeySpacing] because the
     *  strip's icons are smaller and need air to avoid mis-tap. */
    val TopStripKeyGap: Dp = BaseUnit                      // 4dp

    /** Spacing between bottom-bar buttons — widest gap in the system, since
     *  paste/space/enter are the highest-frequency targets and benefit from
     *  generous separation. */
    val BottomBarSpacing: Dp = BaseUnit * 1.5f             // 6dp

    // ----- Pinned widths (ergonomically driven, not grid-derived) -----------
    /** Width of an icon chip in the top strip. Sized for a 44dp touch target
     *  per Material guidance — separate from [TopStripHeight] because some
     *  icons (Settings, Logs, Backspace) live in the strip without their own
     *  text label. */
    val TopStripIconWidth: Dp = 44.dp

    /** Fixed width of the configurable preset slot in the top strip. Wide
     *  enough to fit the longest mode label ("Claude Code") at 11sp. */
    val ModeChipWidth: Dp = 108.dp

    /** Fixed Enter-key width on the universal bottom bar so its screen
     *  position is identical across every layout. */
    val EnterKeyWidth: Dp = 60.dp
}
