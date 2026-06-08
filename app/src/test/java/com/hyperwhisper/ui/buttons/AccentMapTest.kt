package com.hyperwhisper.ui.buttons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the long-press accent/diacritic map that powers the
 * "hold a → à á â ä …" popups.
 */
class AccentMapTest {

    @Test
    fun accentsFor_latinVowel_includesCommonDiacritics() {
        val a = AccentMap.accentsFor("a")
        assertTrue("à expected", a.contains("à"))
        assertTrue("á expected", a.contains("á"))
        assertTrue("ä expected", a.contains("ä"))
        assertTrue("å expected", a.contains("å"))
    }

    @Test
    fun accentsFor_isCaseInsensitive() {
        assertEquals(AccentMap.accentsFor("a"), AccentMap.accentsFor("A"))
        assertEquals(AccentMap.accentsFor("e"), AccentMap.accentsFor("E"))
    }

    @Test
    fun accentsFor_coversKeyEuropeanSpecials() {
        assertTrue(AccentMap.accentsFor("c").contains("ç")) // French/Portuguese
        assertTrue(AccentMap.accentsFor("g").contains("ğ")) // Turkish
        assertTrue(AccentMap.accentsFor("l").contains("ł")) // Polish
        assertTrue(AccentMap.accentsFor("s").contains("ß")) // German
        assertTrue(AccentMap.accentsFor("o").contains("ö")) // German/Swedish
        assertTrue(AccentMap.accentsFor("n").contains("ñ")) // Spanish
    }

    @Test
    fun accentsFor_cyrillicVariants() {
        assertTrue(AccentMap.accentsFor("е").contains("ё")) // Russian
        assertTrue(AccentMap.accentsFor("г").contains("ґ")) // Ukrainian
    }

    @Test
    fun accentsFor_letterWithoutVariants_isEmpty() {
        assertTrue(AccentMap.accentsFor("q").isEmpty())
        assertTrue(AccentMap.accentsFor("1").isEmpty())
        assertTrue(AccentMap.accentsFor("").isEmpty())
    }

    @Test
    fun accentsFor_noDuplicatesAndExcludesBaseLetter() {
        // The popup prepends the base letter itself, so the variant list must
        // not contain the unaccented form, and must be free of duplicates.
        listOf("a", "e", "i", "o", "u", "c", "s", "n", "z").forEach { base ->
            val variants = AccentMap.accentsFor(base)
            assertTrue("$base should not list itself", !variants.contains(base))
            assertEquals("$base has duplicate variants", variants.size, variants.distinct().size)
        }
    }
}
