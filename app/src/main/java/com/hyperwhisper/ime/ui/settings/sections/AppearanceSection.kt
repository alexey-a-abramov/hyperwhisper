package com.hyperwhisper.ui.settings.sections

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.AppearanceSettings
import com.hyperwhisper.ui.settings.components.selectors.ColorSchemeSelector
import com.hyperwhisper.ui.settings.components.selectors.DarkModeSelector
import com.hyperwhisper.ui.settings.components.selectors.FontFamilySelector
import com.hyperwhisper.ui.settings.components.selectors.UILanguageSelector
import com.hyperwhisper.ui.settings.components.selectors.UIScaleSelector
import com.hyperwhisper.ui.settings.dialogs.HistoryReductionWarningDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSection(
    appearanceSettings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit
) {
    var localSettings by remember { mutableStateOf(appearanceSettings) }

    // Update when settings change externally
    LaunchedEffect(appearanceSettings) {
        localSettings = appearanceSettings
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Color Scheme Selector
        Text(
            text = "Color Scheme",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        ColorSchemeSelector(
            selectedScheme = localSettings.colorScheme,
            onSchemeSelected = { scheme ->
                val newSettings = localSettings.copy(colorScheme = scheme)
                localSettings = newSettings
                onSettingsChange(newSettings)
            }
        )

        // Dynamic Color Toggle (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use Dynamic Color",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Match system wallpaper colors",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = localSettings.useDynamicColor,
                    onCheckedChange = { enabled ->
                        val newSettings = localSettings.copy(useDynamicColor = enabled)
                        localSettings = newSettings
                        onSettingsChange(newSettings)
                    }
                )
            }
        }

        Divider()

        // Dark Mode Preference Selector
        Text(
            text = "Theme Mode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        DarkModeSelector(
            selectedMode = localSettings.darkModePreference,
            onModeSelected = { mode ->
                val newSettings = localSettings.copy(darkModePreference = mode)
                localSettings = newSettings
                onSettingsChange(newSettings)
            }
        )

        Divider()

        // UI Scale Selector
        Text(
            text = "Text Size",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        UIScaleSelector(
            selectedScale = localSettings.uiScale,
            onScaleSelected = { scale ->
                val newSettings = localSettings.copy(uiScale = scale)
                localSettings = newSettings
                onSettingsChange(newSettings)
            }
        )

        Divider()

        // Font Family Selector
        Text(
            text = "Font Family",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        FontFamilySelector(
            selectedFont = localSettings.fontFamily,
            onFontSelected = { font ->
                val newSettings = localSettings.copy(fontFamily = font)
                localSettings = newSettings
                onSettingsChange(newSettings)
            }
        )

        Divider()

        // UI Language Selector
        Text(
            text = "Interface Language",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        UILanguageSelector(
            selectedLanguageCode = localSettings.uiLanguage,
            onLanguageSelected = { languageCode ->
                val newSettings = localSettings.copy(uiLanguage = languageCode)
                localSettings = newSettings
                onSettingsChange(newSettings)
            }
        )

        Divider()

        // Feature Toggles
        Text(
            text = "Features",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        // Auto-copy to clipboard toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-copy to Clipboard",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Automatically copy transcribed text to clipboard",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Switch(
                checked = localSettings.autoCopyToClipboard,
                onCheckedChange = { enabled ->
                    val newSettings = localSettings.copy(autoCopyToClipboard = enabled)
                    localSettings = newSettings
                    onSettingsChange(newSettings)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // History panel toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enable History Panel",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Long press paste button to view recent transcriptions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Switch(
                checked = localSettings.enableHistoryPanel,
                onCheckedChange = { enabled ->
                    val newSettings = localSettings.copy(enableHistoryPanel = enabled)
                    localSettings = newSettings
                    onSettingsChange(newSettings)
                }
            )
        }

        // History size configuration (shown when history panel is enabled)
        if (localSettings.enableHistoryPanel) {
            Spacer(modifier = Modifier.height(8.dp))

            // Unlimited history toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Unlimited History",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Keep all transcriptions (may use more storage)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = localSettings.unlimitedHistory,
                    onCheckedChange = { enabled ->
                        val newSettings = localSettings.copy(unlimitedHistory = enabled)
                        localSettings = newSettings
                        onSettingsChange(newSettings)
                    }
                )
            }

            // Max history items (shown when unlimited is off)
            if (!localSettings.unlimitedHistory) {
                Spacer(modifier = Modifier.height(8.dp))

                var maxHistoryText by remember(localSettings.maxHistoryItems) {
                    mutableStateOf(localSettings.maxHistoryItems.toString())
                }
                var showHistoryWarning by remember { mutableStateOf(false) }
                var pendingMaxHistory by remember { mutableStateOf(0) }

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Maximum History Items",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Number of transcriptions to keep (1-100)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = maxHistoryText,
                        onValueChange = { newValue ->
                            maxHistoryText = newValue
                            val newMax = newValue.toIntOrNull()
                            if (newMax != null && newMax in 1..100) {
                                if (newMax < localSettings.maxHistoryItems) {
                                    // Reducing history - show warning
                                    pendingMaxHistory = newMax
                                    showHistoryWarning = true
                                } else {
                                    // Increasing or same - apply immediately
                                    val newSettings = localSettings.copy(maxHistoryItems = newMax)
                                    localSettings = newSettings
                                    onSettingsChange(newSettings)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = maxHistoryText.toIntOrNull()?.let { it !in 1..100 } ?: true
                    )
                }

                // History reduction warning dialog
                if (showHistoryWarning) {
                    HistoryReductionWarningDialog(
                        currentSize = localSettings.maxHistoryItems,
                        newSize = pendingMaxHistory,
                        onConfirm = {
                            val newSettings = localSettings.copy(maxHistoryItems = pendingMaxHistory)
                            localSettings = newSettings
                            onSettingsChange(newSettings)
                            showHistoryWarning = false
                        },
                        onDismiss = {
                            maxHistoryText = localSettings.maxHistoryItems.toString()
                            showHistoryWarning = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Save original audio files toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Save Original Audio Files",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Store recordings for playback and reprocessing from history",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Switch(
                checked = localSettings.saveOriginalAudioFiles,
                onCheckedChange = { enabled ->
                    val newSettings = localSettings.copy(saveOriginalAudioFiles = enabled)
                    localSettings = newSettings
                    onSettingsChange(newSettings)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Techie mode toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Techie Mode",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Show technical details like logs button and field info on keyboard",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Switch(
                checked = localSettings.techieModeEnabled,
                onCheckedChange = { enabled ->
                    val newSettings = localSettings.copy(techieModeEnabled = enabled)
                    localSettings = newSettings
                    onSettingsChange(newSettings)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Miscellaneous section
        Text(
            text = "Miscellaneous",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Show keyboard switcher toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Show Keyboard Switcher",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Display keyboard switcher button next to mode selector",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Switch(
                checked = localSettings.showKeyboardSwitcher,
                onCheckedChange = { enabled ->
                    val newSettings = localSettings.copy(showKeyboardSwitcher = enabled)
                    localSettings = newSettings
                    onSettingsChange(newSettings)
                }
            )
        }
    }
}
