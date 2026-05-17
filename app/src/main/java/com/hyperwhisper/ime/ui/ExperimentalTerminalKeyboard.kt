package com.hyperwhisper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.TranscriptionHistoryItem
import com.hyperwhisper.ui.sections.KeyboardBottomBar

/**
 * Experimental Terminal keyboard.
 *
 * Compact terminal-control layout for Termux + Claude Code. Every chip
 * commits literal bytes that a terminal emulator and bash readline already
 * understand:
 *
 *  - **ASCII control characters** (Ctrl+X = byte X−64): `^C` → 0x03 (SIGINT),
 *    `^L` → 0x0C (clear screen), `^R` → 0x12 (reverse-i-search), etc.
 *  - **xterm escape sequences**: arrows, Home/End, and the back-tab
 *    (`⇧Tab` → ESC [ Z) we use to cycle Claude Code's permission mode.
 *
 * Why bytes, not key chords: terminal emulators read stdin from the PTY and
 * dispatch via readline / curses. They ignore the IME meta-state that an
 * Android KeyEvent would carry — which is why the sticky Ctrl/Alt/Shift
 * toggles on [CodeKeyboard] don't actually produce a working Ctrl+C in
 * Termux. Inserting raw bytes is the only cross-app way to deliver these
 * chords reliably.
 *
 * Curation principle: include only chords that fire often in Claude Code +
 * Termux daily use. Skipped on purpose: `^Z` (suspend — rare in agent
 * workflows), `^G` (only useful inside `^R`), `^B`/`^F` (arrows do it),
 * `⌥B`/`⌥F`/`⌥.` (power-user word movement), `^K`/`^Y` (kill-fwd / yank),
 * `^P`/`^N` (arrows do it), PgUp/PgDn, shell-glue operators (`|`, `>`, `&&`
 * …). If any of those turn out to be missed, add them back here — but
 * starting lean beats a 40-key wall of glyphs.
 *
 * Layout — 3 rows × 6 chips, each row weighted to fill available height
 * (≈ 74dp per chip on a 320dp board, well above Material's 48dp guideline):
 *
 * ```
 *  Row 1 — Modes & cancels:    ⇧Tab  Esc  ^C  ^D  ^L  ^R
 *  Row 2 — Line editing:       ^A    ^E   ^W  ^U  Tab  ⌫
 *  Row 3 — Cursor & nav:       ←     ↓    ↑   →   Hm   End
 * ```
 *
 * `⇧Tab` is the leftmost chip in row 1 — it's the load-bearing key for
 * Claude Code mode cycling, deliberately placed where the thumb lands.
 * `^C` is tinted soft-red as the only "be careful, sends SIGINT" chip.
 */
@Composable
fun ExperimentalTerminalKeyboard(
    onTextCommit: (String) -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    lastTranscribedText: String,
    transcriptionHistory: List<TranscriptionHistoryItem>,
    enableHistoryPanel: Boolean,
    onPasteText: (String) -> Unit,
    onShowHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = KeyboardSurfaceColor,
        shape = RoundedCornerShape(KeyboardMetrics.SurfaceRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(KeyboardMetrics.OuterPadding),
            verticalArrangement = Arrangement.spacedBy(KeyboardMetrics.RowGap)
        ) {
            // weight(1f) on the typing-row Column claims everything above
            // the bottom bar; the three rows split that space equally, so
            // chips end up ~74dp tall on a 320dp board. Far more thumb-able
            // than the 36dp KeyHeightCompact other layouts use, which fits
            // the "fewer, better" curation philosophy of this layout.
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(KeyboardMetrics.RowGap)
            ) {
                TerminalKeyRow(ModesRow, Modifier.weight(1f), onTextCommit)
                TerminalKeyRow(EditRow, Modifier.weight(1f), onTextCommit)
                TerminalKeyRow(CursorRow, Modifier.weight(1f), onTextCommit)
            }

            KeyboardBottomBar(
                lastTranscribedText = lastTranscribedText,
                transcriptionHistory = transcriptionHistory,
                enableHistoryPanel = enableHistoryPanel,
                onPasteText = onPasteText,
                onShowHistory = onShowHistory,
                onSpace = onSpace,
                onEnter = onEnter
            )
        }
    }
}

/**
 * A single terminal chip. [insertion] is committed verbatim; the IME's
 * commitText path forwards it to the focused app's input buffer, which
 * in a terminal emulator means the PTY's stdin.
 */
private data class TerminalKey(
    val label: String,
    val insertion: String,
    /** Optional tint applied to the chip background — used to highlight
     *  signal chips (^C / ^Z) so the eye lands on them. */
    val accent: Color? = null
)

// Soft red for ^C — sends SIGINT. Less saturated than the universal
// backspace red so it reads as "be careful" rather than "delete".
private val SignalAccent = Color(0xFFE57373)

// Row 1 — modes & cancels. ⇧Tab leads: it's the load-bearing chip for
// Claude Code's permission-mode cycle and the user's primary reason this
// layout exists. ^C tinted as the one chord that can drop a running task.
private val ModesRow = listOf(
    TerminalKey("⇧Tab", "\u001B[Z"),               // CSI Z — Claude Code mode cycle
    TerminalKey("Esc", "\u001B"),                  // ESC — vim normal, cancel TUI
    TerminalKey("^C", "\u0003", SignalAccent),     // SIGINT — cancel running command
    TerminalKey("^D", "\u0004"),                   // EOF — exit shell / close stdin
    TerminalKey("^L", "\u000C"),                   // clear screen
    TerminalKey("^R", "\u0012")                    // reverse-i-search history
)

// Row 2 — line editing. The chips you reach for when fixing a long prompt
// before sending it. Tab + Backspace round out the editing toolkit.
private val EditRow = listOf(
    TerminalKey("^A", "\u0001"),                   // jump to start of line
    TerminalKey("^E", "\u0005"),                   // jump to end of line
    TerminalKey("^W", "\u0017"),                   // delete previous word
    TerminalKey("^U", "\u0015"),                   // kill line before cursor
    TerminalKey("Tab", "\t"),                      // tab — completion
    TerminalKey("⌫", "\u007F")                     // DEL — bash binds to backspace
)

// Row 3 — cursor & nav. Arrows double as bash history navigation, so
// dedicated history chips (^P/^N) would be redundant.
private val CursorRow = listOf(
    TerminalKey("←", "\u001B[D"),
    TerminalKey("↓", "\u001B[B"),
    TerminalKey("↑", "\u001B[A"),
    TerminalKey("→", "\u001B[C"),
    TerminalKey("Hm", "\u001B[H"),                 // Home — ESC[H
    TerminalKey("End", "\u001B[F")                 // End — ESC[F
)

@Composable
private fun TerminalKeyRow(
    keys: List<TerminalKey>,
    modifier: Modifier,
    onTextCommit: (String) -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeyboardMetrics.KeySpacing)
    ) {
        keys.forEach { k ->
            Surface(
                onClick = { onTextCommit(k.insertion) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
                color = k.accent ?: KeyboardKeyColor,
                tonalElevation = 1.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = k.label,
                        // Bigger glyphs to match the taller chips. Monospace
                        // keeps `^C`, `⇧Tab`, `Hm` visually aligned even
                        // though their character widths differ.
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = if (k.accent != null) Color.White else KeyboardKeyTextColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
