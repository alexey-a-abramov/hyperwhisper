package com.hyperwhisper.ui.settings.sections

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hyperwhisper.data.AppearanceSettings
import com.hyperwhisper.ui.settings.components.selectors.ColorSchemeSelector
import com.hyperwhisper.ui.settings.components.selectors.DarkModeSelector
import com.hyperwhisper.ui.settings.components.selectors.FontFamilySelector
import com.hyperwhisper.ui.settings.components.selectors.UILanguageSelector
import com.hyperwhisper.ui.settings.components.selectors.UIScaleSelector

/**
 * Theming-only Appearance section. Behavior toggles live in KeyboardBehaviorSection.
 */
@Composable
fun AppearanceSection(
    appearanceSettings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var localSettings by remember(appearanceSettings) { mutableStateOf(appearanceSettings) }

    LaunchedEffect(appearanceSettings) {
        localSettings = appearanceSettings
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        GroupHeader("Color")
        ColorSchemeSelector(
            selectedScheme = localSettings.colorScheme,
            onSchemeSelected = { scheme ->
                val updated = localSettings.copy(colorScheme = scheme)
                localSettings = updated
                onSettingsChange(updated)
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ToggleRow(
                title = "Dynamic color",
                description = "Match your wallpaper colors (Material You)",
                checked = localSettings.useDynamicColor,
                onChange = { enabled ->
                    val updated = localSettings.copy(useDynamicColor = enabled)
                    localSettings = updated
                    onSettingsChange(updated)
                }
            )
        }

        Spacer(Modifier.height(4.dp))
        GroupHeader("Theme")
        DarkModeSelector(
            selectedMode = localSettings.darkModePreference,
            onModeSelected = { mode ->
                val updated = localSettings.copy(darkModePreference = mode)
                localSettings = updated
                onSettingsChange(updated)
            }
        )

        Spacer(Modifier.height(4.dp))
        GroupHeader("Typography")
        Text("Text size", style = MaterialTheme.typography.bodyLarge)
        UIScaleSelector(
            selectedScale = localSettings.uiScale,
            onScaleSelected = { scale ->
                val updated = localSettings.copy(uiScale = scale)
                localSettings = updated
                onSettingsChange(updated)
            }
        )
        Spacer(Modifier.height(8.dp))
        Text("Font family", style = MaterialTheme.typography.bodyLarge)
        FontFamilySelector(
            selectedFont = localSettings.fontFamily,
            onFontSelected = { font ->
                val updated = localSettings.copy(fontFamily = font)
                localSettings = updated
                onSettingsChange(updated)
            }
        )

        Spacer(Modifier.height(4.dp))
        GroupHeader("Interface language")
        UILanguageSelector(
            selectedLanguageCode = localSettings.uiLanguage,
            onLanguageSelected = { code ->
                val updated = localSettings.copy(uiLanguage = code)
                localSettings = updated
                onSettingsChange(updated)
            }
        )
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
