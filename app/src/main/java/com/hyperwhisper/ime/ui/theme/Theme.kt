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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.hyperwhisper.data.AppearanceSettings
import com.hyperwhisper.data.ColorSchemeOption

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
 */
private fun getColorScheme(option: ColorSchemeOption, darkTheme: Boolean): ColorScheme {
    return if (darkTheme) {
        darkColorScheme(
            primary = option.seedColor,
            secondary = Color(0xFF03DAC6),
            tertiary = option.seedColor
        )
    } else {
        lightColorScheme(
            primary = option.seedColor,
            secondary = Color(0xFF03DAC6),
            tertiary = option.seedColor,
            background = Color(0xFFFFFBFE),
            surface = Color(0xFFFFFBFE),
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onTertiary = Color.White,
            onBackground = Color(0xFF1C1B1F),
            onSurface = Color(0xFF1C1B1F)
        )
    }
}

@Composable
fun HyperWhisperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appearanceSettings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        appearanceSettings.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getColorScheme(appearanceSettings.colorScheme, darkTheme)
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
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = scaledTypography,
        content = content
    )
}
