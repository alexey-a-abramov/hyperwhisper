package com.hyperwhisper.ui.util

/**
 * Compact relative-time formatter for the per-provider "last successful test"
 * badge. Optimised for at-a-glance scanning in pickers and settings rows —
 * three or four characters max where possible.
 *
 * Returns one of:
 *  - `null` if [timestampMillis] is null or 0 (i.e. never tested) — caller
 *    decides how to render the "untested" state.
 *  - "now" within the first minute,
 *  - "Nm" / "Nh" / "Nd" up to the staleness threshold,
 *  - "stale" beyond the staleness threshold (default 7 days).
 *
 * Pure function; no Context or Locale required (the badge text is symbolic).
 */
fun formatTestedAgo(
    timestampMillis: Long?,
    nowMillis: Long = System.currentTimeMillis(),
    staleAfterMillis: Long = TESTED_STALE_THRESHOLD_MS,
): String? {
    if (timestampMillis == null || timestampMillis <= 0L) return null
    val deltaMs = (nowMillis - timestampMillis).coerceAtLeast(0L)
    if (deltaMs >= staleAfterMillis) return "stale"
    val deltaMinutes = deltaMs / 60_000
    if (deltaMinutes < 1) return "now"
    if (deltaMinutes < 60) return "${deltaMinutes}m"
    val deltaHours = deltaMinutes / 60
    if (deltaHours < 24) return "${deltaHours}h"
    val deltaDays = deltaHours / 24
    return "${deltaDays}d"
}

/** Default staleness threshold — 7 days in millis. Beyond this the badge
 *  switches from "Nd" to "stale" so the user knows a re-test is warranted. */
const val TESTED_STALE_THRESHOLD_MS: Long = 7L * 24 * 60 * 60 * 1000
