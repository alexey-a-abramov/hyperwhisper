package com.hyperwhisper.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.hyperwhisper.data.AppearanceSettings
import com.hyperwhisper.data.ColorSchemeOption
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.localization.getLanguageByCode
import com.hyperwhisper.localization.getStrings

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6200EE),
    secondary = Color(0xFF03DAC6),
    tertiary = Color(0xFF3700B3)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6200EE),
    secondary = Color(0xFF03DAC6),
    tertiary = Color(0xFF3700B3),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

/**
 * CompositionLocal for AppearanceSettings
 */
val LocalAppearanceSettings = compositionLocalOf { AppearanceSettings() }

/**
 * Get color scheme based on the selected option
 * Provides accessible dark and light versions of each theme
 */
private fun getColorScheme(option: ColorSchemeOption, darkTheme: Boolean): ColorScheme {
    return if (darkTheme) {
        // Dark color schemes with high contrast for accessibility
        darkColorScheme(
            primary = option.primaryColor,
            onPrimary = Color.Black,
            primaryContainer = option.primaryColor.copy(alpha = 0.3f),
            onPrimaryContainer = option.primaryColor.copy(alpha = 0.9f),

            secondary = option.secondaryColor,
            onSecondary = Color.Black,
            secondaryContainer = option.secondaryColor.copy(alpha = 0.3f),
            onSecondaryContainer = option.secondaryColor.copy(alpha = 0.9f),

            tertiary = option.tertiaryColor,
            onTertiary = Color.Black,
            tertiaryContainer = option.tertiaryColor.copy(alpha = 0.3f),
            onTertiaryContainer = option.tertiaryColor.copy(alpha = 0.9f),

            background = Color(0xFF121212),
            onBackground = Color(0xFFE0E0E0),

            surface = Color(0xFF1E1E1E),
            onSurface = Color(0xFFE0E0E0),
            surfaceVariant = Color(0xFF2C2C2C),
            onSurfaceVariant = Color(0xFFB0B0B0),

            error = Color(0xFFCF6679),
            onError = Color.Black,
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFB4AB),

            outline = Color(0xFF5F5F5F),
            outlineVariant = Color(0xFF3F3F3F)
        )
    } else {
        // Light color schemes with good contrast
        lightColorScheme(
            primary = option.primaryColor,
            onPrimary = Color.White,
            primaryContainer = option.primaryColor.copy(alpha = 0.2f),
            onPrimaryContainer = option.primaryColor.copy(alpha = 1f),

            secondary = option.secondaryColor,
            onSecondary = Color.White,
            secondaryContainer = option.secondaryColor.copy(alpha = 0.2f),
            onSecondaryContainer = option.secondaryColor.copy(alpha = 1f),

            tertiary = option.tertiaryColor,
            onTertiary = Color.White,
            tertiaryContainer = option.tertiaryColor.copy(alpha = 0.2f),
            onTertiaryContainer = option.tertiaryColor.copy(alpha = 1f),

            background = Color(0xFFFFFBFE),
            onBackground = Color(0xFF1C1B1F),

            surface = Color(0xFFFFFBFE),
            onSurface = Color(0xFF1C1B1F),
            surfaceVariant = Color(0xFFF3F0F4),
            onSurfaceVariant = Color(0xFF49454F),

            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),

            outline = Color(0xFF79747E),
            outlineVariant = Color(0xFFCAC4D0)
        )
    }
}

@Composable
fun HyperWhisperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appearanceSettings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit
) {
    // Determine actual dark mode based on user preference
    val actualDarkTheme = when (appearanceSettings.darkModePreference) {
        com.hyperwhisper.data.DarkModePreference.SYSTEM -> darkTheme
        com.hyperwhisper.data.DarkModePreference.LIGHT -> false
        com.hyperwhisper.data.DarkModePreference.DARK -> true
    }

    val colorScheme = when {
        appearanceSettings.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (actualDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getColorScheme(appearanceSettings.colorScheme, actualDarkTheme)
    }

    // Create scaled typography based on appearance settings
    val scaledTypography = createScaledTypography(
        scale = appearanceSettings.uiScale.scale,
        fontFamily = appearanceSettings.fontFamily.fontFamily
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.statusBarColor = colorScheme.primary.toArgb()
            window?.let {
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !actualDarkTheme
            }
        }
    }

    // Get strings and language for selected UI language
    val language = getLanguageByCode(appearanceSettings.uiLanguage)
    val strings = language.getStrings()

    // Determine layout direction based on language
    val layoutDirection = if (language.isRTL) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    CompositionLocalProvider(
        LocalStrings provides strings,
        LocalLayoutDirection provides layoutDirection
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography,
            content = content
        )
    }
}
