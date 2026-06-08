package com.hyperwhisper.data

/**
 * Pure locality (keyboard-language) selection logic, extracted from the
 * keyboard Compose tree so it can be unit-tested without a UI.
 *
 * A "locality" is a [KeyboardLayout]; the active one drives both the typing
 * layout and the speech-input language. The tap-cycle rotates only through
 * the *enabled* set (in enum order), while the long-press list can enable or
 * disable any locality.
 */
object LocalitySelection {

    /**
     * Enabled localities in canonical (enum) order. Always non-empty — an
     * empty enabled set falls back to English so the cycle never stalls.
     */
    fun enabledInOrder(enabled: Set<KeyboardLayout>): List<KeyboardLayout> =
        KeyboardLayout.values().filter { it in enabled }
            .ifEmpty { listOf(KeyboardLayout.ENGLISH) }

    /**
     * The next enabled locality after [current], wrapping around. If [current]
     * isn't in the enabled set, returns the first enabled locality.
     */
    fun next(current: KeyboardLayout, enabled: Set<KeyboardLayout>): KeyboardLayout {
        val order = enabledInOrder(enabled)
        val idx = order.indexOf(current)
        // current not enabled → start the cycle at the first enabled locality.
        return if (idx < 0) order.first() else order[(idx + 1) % order.size]
    }

    /**
     * Toggle [layout]'s membership in the enabled (tap-cycle) set. Never
     * returns an empty set — removing the last entry falls back to English.
     */
    fun toggleEnabled(enabled: Set<KeyboardLayout>, layout: KeyboardLayout): Set<KeyboardLayout> {
        val next = if (layout in enabled) enabled - layout else enabled + layout
        return next.ifEmpty { setOf(KeyboardLayout.ENGLISH) }
    }
}
