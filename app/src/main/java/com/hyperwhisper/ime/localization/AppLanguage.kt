package com.hyperwhisper.localization

import androidx.compose.runtime.compositionLocalOf

/**
 * Available UI languages for HyperWhisper
 *
 * To add a new language:
 * 1. Add enum entry here
 * 2. Create Strings implementation (e.g., GermanStrings.kt)
 * 3. Add mapping in getStrings() function below
 */
enum class AppLanguage(
    val displayName: String,
    val nativeName: String,
    val code: String,
    val isRTL: Boolean = false
) {
    ENGLISH(
        displayName = "English",
        nativeName = "English",
        code = "en",
        isRTL = false
    ),
    RUSSIAN(
        displayName = "Russian",
        nativeName = "Русский",
        code = "ru",
        isRTL = false
    ),
    ARABIC(
        displayName = "Arabic",
        nativeName = "العربية",
        code = "ar",
        isRTL = true
    );
    // Add more languages here:
    // GERMAN("German", "Deutsch", "de", false),
    // SPANISH("Spanish", "Español", "es", false),
    // FRENCH("French", "Français", "fr", false),
    // HEBREW("Hebrew", "עברית", "he", true),
    // FARSI("Farsi", "فارسی", "fa", true),
    // etc.
}

/**
 * Get Strings implementation for a specific language
 */
fun AppLanguage.getStrings(): Strings {
    return when (this) {
        AppLanguage.ENGLISH -> EnglishStrings
        AppLanguage.RUSSIAN -> RussianStrings
        AppLanguage.ARABIC -> ArabicStrings
        // Add new languages here:
        // AppLanguage.GERMAN -> GermanStrings
        // AppLanguage.SPANISH -> SpanishStrings
    }
}

/**
 * Get language by code, defaults to English if not found
 */
fun getLanguageByCode(code: String): AppLanguage {
    return AppLanguage.values().firstOrNull { it.code == code } ?: AppLanguage.ENGLISH
}

/**
 * CompositionLocal to provide strings throughout the app
 * Usage in Composables: val strings = LocalStrings.current
 */
val LocalStrings = compositionLocalOf<Strings> { EnglishStrings }
