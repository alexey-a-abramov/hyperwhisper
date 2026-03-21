package com.hyperwhisper.ui

import com.hyperwhisper.data.KeyboardLayout

/**
 * Keyboard layout definitions for different languages
 */
object KeyboardLayouts {

    // English QWERTY Layout
    val englishLayout = LayoutDefinition(
        topRow = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        middleRow = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        bottomRow = listOf("z", "x", "c", "v", "b", "n", "m"),
        shiftedSymbols = mapOf(
            "1" to "!", "2" to "@", "3" to "#", "4" to "$", "5" to "%",
            "6" to "^", "7" to "&", "8" to "*", "9" to "(", "0" to ")"
        )
    )

    // Russian ЙЦУКЕН Layout
    val russianLayout = LayoutDefinition(
        topRow = listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ"),
        middleRow = listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
        bottomRow = listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю"),
        shiftedSymbols = mapOf(
            "1" to "!", "2" to "\"", "3" to "№", "4" to ";", "5" to "%",
            "6" to ":", "7" to "?", "8" to "*", "9" to "(", "0" to ")",
            "." to ",", "," to "."
        )
    )

    // Spanish QWERTY Layout (with ñ)
    val spanishLayout = LayoutDefinition(
        topRow = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        middleRow = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ"),
        bottomRow = listOf("z", "x", "c", "v", "b", "n", "m"),
        shiftedSymbols = mapOf(
            "1" to "!", "2" to "@", "3" to "#", "4" to "$", "5" to "%",
            "6" to "^", "7" to "&", "8" to "*", "9" to "(", "0" to ")"
        )
    )

    // French AZERTY Layout
    val frenchLayout = LayoutDefinition(
        topRow = listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p"),
        middleRow = listOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m"),
        bottomRow = listOf("w", "x", "c", "v", "b", "n"),
        shiftedSymbols = mapOf(
            "1" to "&", "2" to "é", "3" to "\"", "4" to "'", "5" to "(",
            "6" to "-", "7" to "è", "8" to "_", "9" to "ç", "0" to "à"
        )
    )

    // German QWERTZ Layout
    val germanLayout = LayoutDefinition(
        topRow = listOf("q", "w", "e", "r", "t", "z", "u", "i", "o", "p", "ü"),
        middleRow = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ö", "ä"),
        bottomRow = listOf("y", "x", "c", "v", "b", "n", "m"),
        shiftedSymbols = mapOf(
            "1" to "!", "2" to "\"", "3" to "§", "4" to "$", "5" to "%",
            "6" to "&", "7" to "/", "8" to "(", "9" to ")", "0" to "="
        )
    )

    // Arabic Layout (right-to-left)
    val arabicLayout = LayoutDefinition(
        topRow = listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج", "د"),
        middleRow = listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط"),
        bottomRow = listOf("ئ", "ء", "ؤ", "ر", "لا", "ى", "ة", "و", "ز", "ظ"),
        shiftedSymbols = mapOf(
            "1" to "!", "2" to "@", "3" to "#", "4" to "$", "5" to "%",
            "6" to "^", "7" to "&", "8" to "*", "9" to "(", "0" to ")"
        ),
        isRTL = true
    )

    /**
     * Get layout definition for a specific language
     */
    fun getLayout(layout: KeyboardLayout): LayoutDefinition {
        return when (layout) {
            KeyboardLayout.ENGLISH -> englishLayout
            KeyboardLayout.RUSSIAN -> russianLayout
            KeyboardLayout.SPANISH -> spanishLayout
            KeyboardLayout.FRENCH -> frenchLayout
            KeyboardLayout.GERMAN -> germanLayout
            KeyboardLayout.ARABIC -> arabicLayout
        }
    }
}

/**
 * Layout definition containing key positions and special characters
 */
data class LayoutDefinition(
    val topRow: List<String>,
    val middleRow: List<String>,
    val bottomRow: List<String>,
    val shiftedSymbols: Map<String, String> = emptyMap(),
    val isRTL: Boolean = false // Right-to-left languages
)
