package com.hyperwhisper.ui.buttons

/**
 * Universal diacritic/accent variants surfaced when a letter key is held —
 * the standard "hold a → à á â ä …" affordance. Keyed on the plain ASCII
 * base letter (lowercase) so it works regardless of which locality layout is
 * active: a French AZERTY, German QWERTZ, Turkish Q, or Polish programmer
 * layout all share the same Latin base letters, so one map covers them all
 * (French accents, German/Swedish umlauts, Turkish ğ/ş, Polish ą/ę/ł, …).
 *
 * Variants are listed most-common-first and capped to a comfortable popup
 * width. The base letter itself is NOT included here — the popup prepends it
 * so a plain tap-and-release still types the unaccented letter.
 *
 * A small Cyrillic set is included for the Russian/Ukrainian layouts (е → ё,
 * etc.). Returns an empty list for letters with no variants, in which case the
 * caller renders a plain key.
 */
internal object AccentMap {

    private val variants: Map<String, List<String>> = mapOf(
        // Latin
        "a" to listOf("à", "á", "â", "ä", "ã", "å", "æ", "ā", "ą"),
        "c" to listOf("ç", "ć", "č"),
        "d" to listOf("ď", "đ"),
        "e" to listOf("è", "é", "ê", "ë", "ē", "ę", "ě"),
        "g" to listOf("ğ", "ģ"),
        "i" to listOf("ì", "í", "î", "ï", "ı", "ī", "į"),
        "l" to listOf("ł", "ļ"),
        "n" to listOf("ñ", "ń", "ň"),
        "o" to listOf("ò", "ó", "ô", "ö", "õ", "ø", "ō", "œ"),
        "r" to listOf("ř", "ŗ"),
        "s" to listOf("ś", "š", "ş", "ß"),
        "t" to listOf("ţ", "ť"),
        "u" to listOf("ù", "ú", "û", "ü", "ū", "ů"),
        "y" to listOf("ý", "ÿ"),
        "z" to listOf("ź", "ż", "ž"),
        // Cyrillic (Russian / Ukrainian)
        "е" to listOf("ё"),
        "г" to listOf("ґ"),
        "и" to listOf("і", "ї"),
    )

    /**
     * Accent variants for [base] (matched case-insensitively against the
     * lowercase key). Empty when the letter has none.
     */
    fun accentsFor(base: String): List<String> =
        variants[base.lowercase()] ?: emptyList()
}
