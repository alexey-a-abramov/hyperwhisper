package com.hyperwhisper.ui

import com.hyperwhisper.data.KeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the keyboard locality catalog: every [KeyboardLayout] must
 * have a well-formed descriptor and a renderable layout definition.
 */
class KeyboardLayoutsTest {

    @Test
    fun everyLayout_hasNonBlankDescriptors() {
        KeyboardLayout.values().forEach { layout ->
            assertTrue("${layout.name} code blank", layout.code.isNotBlank())
            assertTrue("${layout.name} displayName blank", layout.displayName.isNotBlank())
            assertTrue("${layout.name} nativeName blank", layout.nativeName.isNotBlank())
            assertTrue("${layout.name} inputLanguageCode blank", layout.inputLanguageCode.isNotBlank())
        }
    }

    @Test
    fun displayCodes_areUnique() {
        val codes = KeyboardLayout.values().map { it.code }
        assertEquals("Duplicate display codes", codes.size, codes.distinct().size)
    }

    @Test
    fun inputLanguageCodes_areLowercaseIso639() {
        KeyboardLayout.values().forEach { layout ->
            val code = layout.inputLanguageCode
            assertEquals("${layout.name} code not lowercase", code.lowercase(), code)
            assertEquals("${layout.name} code not 2 letters", 2, code.length)
        }
    }

    @Test
    fun getLayout_returnsNonEmptyRows_forEveryLayout() {
        KeyboardLayout.values().forEach { layout ->
            val def = KeyboardLayouts.getLayout(layout)
            assertTrue("${layout.name} topRow empty", def.topRow.isNotEmpty())
            assertTrue("${layout.name} middleRow empty", def.middleRow.isNotEmpty())
            assertTrue("${layout.name} bottomRow empty", def.bottomRow.isNotEmpty())
        }
    }

    @Test
    fun russianAndUkrainian_useCyrillicLayouts() {
        assertTrue(KeyboardLayouts.getLayout(KeyboardLayout.RUSSIAN).topRow.contains("й"))
        assertTrue(KeyboardLayouts.getLayout(KeyboardLayout.UKRAINIAN).middleRow.contains("і"))
    }

    @Test
    fun newLatinLayouts_carryTheirSignatureLetters() {
        assertTrue(KeyboardLayouts.getLayout(KeyboardLayout.SWEDISH).middleRow.contains("ö"))
        assertTrue(KeyboardLayouts.getLayout(KeyboardLayout.TURKISH).topRow.contains("ı"))
        assertTrue(KeyboardLayouts.getLayout(KeyboardLayout.PORTUGUESE).middleRow.contains("ç"))
    }

    @Test
    fun arabicIsRtl_othersAreNot() {
        assertTrue(KeyboardLayouts.getLayout(KeyboardLayout.ARABIC).isRTL)
        assertFalse(KeyboardLayouts.getLayout(KeyboardLayout.ENGLISH).isRTL)
    }
}
