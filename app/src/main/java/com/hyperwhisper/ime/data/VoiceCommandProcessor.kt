package com.hyperwhisper.data

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Processes voice commands and applies settings changes
 */
@Singleton
class VoiceCommandProcessor @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val context: Context
) {
    companion object {
        private const val TAG = "VoiceCommandProcessor"
    }

    /**
     * Execute a voice command
     */
    suspend fun executeCommand(
        commandJson: String,
        coroutineScope: CoroutineScope
    ): VoiceCommandResult {
        val command = VoiceCommand.fromJson(commandJson)

        if (command == null) {
            return VoiceCommandResult(
                success = false,
                message = "Failed to parse command"
            )
        }

        Log.d(TAG, "Executing command: $command")

        return when (command.getCommandType()) {
            VoiceCommandType.CHANGE_SETTING -> handleChangeSetting(command, coroutineScope)
            VoiceCommandType.UNKNOWN -> VoiceCommandResult(
                success = false,
                message = "Unknown command: ${command.command}"
            )
        }
    }

    /**
     * Handle change_setting command
     */
    private suspend fun handleChangeSetting(
        command: VoiceCommand,
        coroutineScope: CoroutineScope
    ): VoiceCommandResult {
        return when (command.getSettingType()) {
            SettingType.INPUT_LANGUAGE -> changeInputLanguage(command.value)
            SettingType.OUTPUT_LANGUAGE -> changeOutputLanguage(command.value)
            SettingType.VOICE_MODE -> changeVoiceMode(command.value)
            SettingType.ENABLE_HISTORY -> changeHistoryEnabled(command.getBooleanValue())
            SettingType.UI_LANGUAGE -> changeUILanguage(command.value)
            SettingType.THEME -> changeTheme(command.getThemeOption())
            SettingType.ENABLE_TECHIE_MODE -> changeTechieModeEnabled(command.getBooleanValue())
            SettingType.ENABLE_CONFIGURATION_MODE -> changeConfigurationModeEnabled(command.getBooleanValue())
            SettingType.UNKNOWN -> VoiceCommandResult(
                success = false,
                message = "Unknown setting: ${command.setting}"
            )
        }
    }

    /**
     * Change input language
     */
    private suspend fun changeInputLanguage(languageCode: String): VoiceCommandResult {
        val language = findLanguageByCodeOrName(languageCode)
        if (language == null) {
            return VoiceCommandResult(
                success = false,
                message = "Language not found: $languageCode"
            )
        }

        val currentSettings = settingsRepository.apiSettings.first()
        val updatedSettings = currentSettings.copy(inputLanguage = language.code)
        settingsRepository.saveApiSettings(updatedSettings)

        return VoiceCommandResult(
            success = true,
            message = "Input language changed to ${language.name}",
            settingChanged = "Input Language",
            newValue = language.name
        )
    }

    /**
     * Change output language
     */
    private suspend fun changeOutputLanguage(languageCode: String): VoiceCommandResult {
        val language = findLanguageByCodeOrName(languageCode)
        if (language == null) {
            return VoiceCommandResult(
                success = false,
                message = "Language not found: $languageCode"
            )
        }

        val currentSettings = settingsRepository.apiSettings.first()
        val updatedSettings = currentSettings.copy(outputLanguage = language.code)
        settingsRepository.saveApiSettings(updatedSettings)

        return VoiceCommandResult(
            success = true,
            message = "Output language changed to ${language.name}",
            settingChanged = "Output Language",
            newValue = language.name
        )
    }

    /**
     * Change voice mode using phonetic matching
     */
    private suspend fun changeVoiceMode(modeName: String): VoiceCommandResult {
        val allModes = settingsRepository.voiceModes.first()
        val matchedMode = findClosestVoiceMode(modeName, allModes)

        if (matchedMode == null) {
            return VoiceCommandResult(
                success = false,
                message = "Voice mode not found: $modeName"
            )
        }

        settingsRepository.setSelectedMode(matchedMode.id)

        return VoiceCommandResult(
            success = true,
            message = "Voice mode changed to ${matchedMode.name}",
            settingChanged = "Voice Mode",
            newValue = matchedMode.name
        )
    }

    /**
     * Enable/disable history
     */
    private suspend fun changeHistoryEnabled(enabled: Boolean): VoiceCommandResult {
        val currentSettings = settingsRepository.appearanceSettings.first()
        val updatedSettings = currentSettings.copy(enableHistoryPanel = enabled)
        settingsRepository.saveAppearanceSettings(updatedSettings)

        val status = if (enabled) "enabled" else "disabled"
        return VoiceCommandResult(
            success = true,
            message = "History $status",
            settingChanged = "History",
            newValue = status
        )
    }

    /**
     * Change UI language
     */
    private suspend fun changeUILanguage(languageCode: String): VoiceCommandResult {
        val appLanguage = findAppLanguageByCodeOrName(languageCode)
        if (appLanguage == null) {
            return VoiceCommandResult(
                success = false,
                message = "UI language not found: $languageCode"
            )
        }

        val currentSettings = settingsRepository.appearanceSettings.first()
        val updatedSettings = currentSettings.copy(uiLanguage = appLanguage.code)
        settingsRepository.saveAppearanceSettings(updatedSettings)

        return VoiceCommandResult(
            success = true,
            message = "UI language changed to ${appLanguage.nativeName}",
            settingChanged = "Interface Language",
            newValue = appLanguage.nativeName
        )
    }

    /**
     * Change theme
     */
    private suspend fun changeTheme(theme: ThemeOption): VoiceCommandResult {
        val currentSettings = settingsRepository.appearanceSettings.first()
        val darkModePreference = when (theme) {
            ThemeOption.SYSTEM -> DarkModePreference.SYSTEM
            ThemeOption.LIGHT -> DarkModePreference.LIGHT
            ThemeOption.DARK -> DarkModePreference.DARK
        }
        val updatedSettings = currentSettings.copy(darkModePreference = darkModePreference)
        settingsRepository.saveAppearanceSettings(updatedSettings)

        val themeName = when (theme) {
            ThemeOption.SYSTEM -> "System"
            ThemeOption.LIGHT -> "Light"
            ThemeOption.DARK -> "Dark"
        }

        return VoiceCommandResult(
            success = true,
            message = "Theme changed to $themeName",
            settingChanged = "Theme",
            newValue = themeName
        )
    }

    /**
     * Enable/disable voice commands mode
     * This will switch to/from the voice commands mode
     * @deprecated Replaced by changeConfigurationModeEnabled
     */
    private suspend fun changeVoiceCommandsEnabled(enabled: Boolean): VoiceCommandResult {
        return changeConfigurationModeEnabled(enabled)
    }

    /**
     * Enable/disable configuration mode
     * This will switch to/from the configuration mode
     */
    private suspend fun changeConfigurationModeEnabled(enabled: Boolean): VoiceCommandResult {
        val allModes = settingsRepository.voiceModes.first()

        if (enabled) {
            // Switch to configuration mode
            val configurationMode = allModes.firstOrNull { it.id == "configuration" }
            if (configurationMode != null) {
                settingsRepository.setSelectedMode(configurationMode.id)
                return VoiceCommandResult(
                    success = true,
                    message = "Configuration mode enabled",
                    settingChanged = "Configuration Mode",
                    newValue = "Enabled"
                )
            } else {
                return VoiceCommandResult(
                    success = false,
                    message = "Configuration mode not found"
                )
            }
        } else {
            // Switch to verbatim mode (default)
            val verbatimMode = allModes.firstOrNull { it.id == "verbatim" }
            if (verbatimMode != null) {
                settingsRepository.setSelectedMode(verbatimMode.id)
                return VoiceCommandResult(
                    success = true,
                    message = "Configuration mode disabled",
                    settingChanged = "Configuration Mode",
                    newValue = "Disabled"
                )
            } else {
                return VoiceCommandResult(
                    success = false,
                    message = "Default mode not found"
                )
            }
        }
    }

    /**
     * Enable/disable techie/developer mode
     */
    private suspend fun changeTechieModeEnabled(enabled: Boolean): VoiceCommandResult {
        val currentSettings = settingsRepository.appearanceSettings.first()
        val updatedSettings = currentSettings.copy(techieModeEnabled = enabled)
        settingsRepository.saveAppearanceSettings(updatedSettings)

        val status = if (enabled) "enabled" else "disabled"
        return VoiceCommandResult(
            success = true,
            message = "Techie mode $status",
            settingChanged = "Techie Mode",
            newValue = status
        )
    }

    /**
     * Find language by code or name using phonetic matching
     */
    private fun findLanguageByCodeOrName(query: String): Language? {
        val normalizedQuery = query.lowercase().trim()

        // First try exact code match
        val exactMatch = SUPPORTED_LANGUAGES.firstOrNull {
            it.code.equals(normalizedQuery, ignoreCase = true)
        }
        if (exactMatch != null) return exactMatch

        // Then try phonetic matching on name
        return findClosestMatch(
            query = normalizedQuery,
            items = SUPPORTED_LANGUAGES,
            nameExtractor = { it.name }
        )
    }

    /**
     * Find app language (UI language) by code or name
     */
    private fun findAppLanguageByCodeOrName(query: String): com.hyperwhisper.localization.AppLanguage? {
        val normalizedQuery = query.lowercase().trim()

        // First try exact code match
        val exactMatch = com.hyperwhisper.localization.AppLanguage.values().firstOrNull {
            it.code.equals(normalizedQuery, ignoreCase = true)
        }
        if (exactMatch != null) return exactMatch

        // Then try phonetic matching on name
        return findClosestMatch(
            query = normalizedQuery,
            items = com.hyperwhisper.localization.AppLanguage.values().toList(),
            nameExtractor = { it.nativeName }
        )
    }

    /**
     * Find closest voice mode using phonetic matching
     */
    private fun findClosestVoiceMode(query: String, modes: List<VoiceMode>): VoiceMode? {
        val normalizedQuery = query.lowercase().trim()

        // First try exact ID match
        val exactMatch = modes.firstOrNull {
            it.id.equals(normalizedQuery, ignoreCase = true)
        }
        if (exactMatch != null) return exactMatch

        // Then try phonetic matching on name
        return findClosestMatch(
            query = normalizedQuery,
            items = modes,
            nameExtractor = { it.name }
        )
    }

    /**
     * Generic phonetic matching using Levenshtein distance
     * Finds the closest match based on edit distance
     */
    private fun <T> findClosestMatch(
        query: String,
        items: List<T>,
        nameExtractor: (T) -> String
    ): T? {
        if (items.isEmpty()) return null

        var bestMatch: T? = null
        var bestDistance = Int.MAX_VALUE

        for (item in items) {
            val name = nameExtractor(item).lowercase()

            // Check for substring match first (high priority)
            if (name.contains(query) || query.contains(name)) {
                val distance = levenshteinDistance(query, name)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestMatch = item
                }
            } else {
                // Use Levenshtein distance for phonetic similarity
                val distance = levenshteinDistance(query, name)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestMatch = item
                }
            }
        }

        // Only return match if it's reasonably close (within 50% of query length)
        val threshold = (query.length * 0.5).toInt().coerceAtLeast(3)
        return if (bestDistance <= threshold) bestMatch else null
    }

    /**
     * Calculate Levenshtein distance (edit distance) between two strings
     * Used for phonetic matching
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[m][n]
    }

    /**
     * Show a toast notification for the command result
     */
    fun showNotification(result: VoiceCommandResult) {
        val message = if (result.success && result.settingChanged != null && result.newValue != null) {
            "✓ ${result.settingChanged}: ${result.newValue}"
        } else {
            result.message
        }

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        Log.d(TAG, "Command result: $message")
    }
}
