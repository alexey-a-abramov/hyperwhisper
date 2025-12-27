package com.hyperwhisper.data

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import android.util.Log

/**
 * Voice command types supported by the system
 */
enum class VoiceCommandType {
    CHANGE_SETTING,
    UNKNOWN
}

/**
 * Settings that can be changed via voice commands
 */
enum class SettingType {
    INPUT_LANGUAGE,
    OUTPUT_LANGUAGE,
    VOICE_MODE,
    ENABLE_HISTORY,
    UI_LANGUAGE,
    THEME,
    ENABLE_VOICE_COMMANDS,
    UNKNOWN
}

/**
 * Theme options
 */
enum class ThemeOption {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Voice command data model
 * Example JSON:
 * {
 *   "command": "change_setting",
 *   "setting": "input_language",
 *   "value": "en"
 * }
 */
data class VoiceCommand(
    val command: String = "",
    val setting: String = "",
    val value: String = ""
) {
    companion object {
        private const val TAG = "VoiceCommand"

        /**
         * Parse JSON string into VoiceCommand
         */
        fun fromJson(json: String): VoiceCommand? {
            return try {
                // Try to extract JSON from response if it contains additional text
                val jsonContent = extractJson(json)
                val gson = Gson()
                gson.fromJson(jsonContent, VoiceCommand::class.java)
            } catch (e: JsonSyntaxException) {
                Log.e(TAG, "Failed to parse voice command JSON: $json", e)
                null
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error parsing voice command", e)
                null
            }
        }

        /**
         * Extract JSON object from text that might contain additional content
         * Looks for first { and last } to extract JSON
         */
        private fun extractJson(text: String): String {
            val startIndex = text.indexOf('{')
            val endIndex = text.lastIndexOf('}')

            return if (startIndex >= 0 && endIndex > startIndex) {
                text.substring(startIndex, endIndex + 1)
            } else {
                text
            }
        }
    }

    /**
     * Get command type
     */
    fun getCommandType(): VoiceCommandType {
        return when (command.lowercase()) {
            "change_setting" -> VoiceCommandType.CHANGE_SETTING
            else -> VoiceCommandType.UNKNOWN
        }
    }

    /**
     * Get setting type
     */
    fun getSettingType(): SettingType {
        return when (setting.lowercase().replace("_", "").replace("-", "")) {
            "inputlanguage", "input" -> SettingType.INPUT_LANGUAGE
            "outputlanguage", "output" -> SettingType.OUTPUT_LANGUAGE
            "voicemode", "mode", "processormode" -> SettingType.VOICE_MODE
            "enablehistory", "history" -> SettingType.ENABLE_HISTORY
            "uilanguage", "interfacelanguage", "language" -> SettingType.UI_LANGUAGE
            "theme", "darkmode", "lightmode" -> SettingType.THEME
            "enablevoicecommands", "voicecommands", "commandmode" -> SettingType.ENABLE_VOICE_COMMANDS
            else -> SettingType.UNKNOWN
        }
    }

    /**
     * Get theme option from value
     */
    fun getThemeOption(): ThemeOption {
        return when (value.lowercase()) {
            "system", "auto", "automatic" -> ThemeOption.SYSTEM
            "light", "day" -> ThemeOption.LIGHT
            "dark", "night" -> ThemeOption.DARK
            else -> ThemeOption.SYSTEM
        }
    }

    /**
     * Get boolean value
     */
    fun getBooleanValue(): Boolean {
        return when (value.lowercase()) {
            "true", "yes", "on", "enable", "enabled", "1" -> true
            "false", "no", "off", "disable", "disabled", "0" -> false
            else -> false
        }
    }
}

/**
 * Result of executing a voice command
 */
data class VoiceCommandResult(
    val success: Boolean,
    val message: String,
    val settingChanged: String? = null,
    val newValue: String? = null
)
