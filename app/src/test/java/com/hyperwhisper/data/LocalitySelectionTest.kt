package com.hyperwhisper.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure locality cycle/toggle logic behind the keyboard's
 * language switcher (tap = cycle enabled, list checkbox = enable/disable).
 */
class LocalitySelectionTest {

    @Test
    fun enabledInOrder_returnsEnumOrder_regardlessOfSetOrder() {
        val enabled = setOf(
            KeyboardLayout.GERMAN,
            KeyboardLayout.ENGLISH,
            KeyboardLayout.RUSSIAN
        )
        assertEquals(
            listOf(KeyboardLayout.ENGLISH, KeyboardLayout.RUSSIAN, KeyboardLayout.GERMAN),
            LocalitySelection.enabledInOrder(enabled)
        )
    }

    @Test
    fun enabledInOrder_emptySet_fallsBackToEnglish() {
        assertEquals(
            listOf(KeyboardLayout.ENGLISH),
            LocalitySelection.enabledInOrder(emptySet())
        )
    }

    @Test
    fun next_wrapsAroundEnabledSet_skippingDisabled() {
        val enabled = setOf(KeyboardLayout.ENGLISH, KeyboardLayout.RUSSIAN, KeyboardLayout.GERMAN)
        assertEquals(KeyboardLayout.RUSSIAN, LocalitySelection.next(KeyboardLayout.ENGLISH, enabled))
        assertEquals(KeyboardLayout.GERMAN, LocalitySelection.next(KeyboardLayout.RUSSIAN, enabled))
        // wraps back to the first enabled
        assertEquals(KeyboardLayout.ENGLISH, LocalitySelection.next(KeyboardLayout.GERMAN, enabled))
    }

    @Test
    fun next_currentNotEnabled_startsAtFirstEnabled() {
        val enabled = setOf(KeyboardLayout.ENGLISH, KeyboardLayout.RUSSIAN, KeyboardLayout.GERMAN)
        // SPANISH isn't enabled — cycle should land on the first enabled (ENGLISH).
        assertEquals(KeyboardLayout.ENGLISH, LocalitySelection.next(KeyboardLayout.SPANISH, enabled))
    }

    @Test
    fun next_singleEnabled_returnsItself() {
        val enabled = setOf(KeyboardLayout.TURKISH)
        assertEquals(KeyboardLayout.TURKISH, LocalitySelection.next(KeyboardLayout.TURKISH, enabled))
    }

    @Test
    fun next_emptyEnabled_returnsEnglish() {
        assertEquals(KeyboardLayout.ENGLISH, LocalitySelection.next(KeyboardLayout.RUSSIAN, emptySet()))
    }

    @Test
    fun toggleEnabled_addsWhenAbsent() {
        val result = LocalitySelection.toggleEnabled(setOf(KeyboardLayout.ENGLISH), KeyboardLayout.POLISH)
        assertEquals(setOf(KeyboardLayout.ENGLISH, KeyboardLayout.POLISH), result)
    }

    @Test
    fun toggleEnabled_removesWhenPresent() {
        val result = LocalitySelection.toggleEnabled(
            setOf(KeyboardLayout.ENGLISH, KeyboardLayout.POLISH),
            KeyboardLayout.POLISH
        )
        assertEquals(setOf(KeyboardLayout.ENGLISH), result)
    }

    @Test
    fun toggleEnabled_removingLast_fallsBackToEnglish() {
        val result = LocalitySelection.toggleEnabled(setOf(KeyboardLayout.RUSSIAN), KeyboardLayout.RUSSIAN)
        assertEquals(setOf(KeyboardLayout.ENGLISH), result)
    }
}
