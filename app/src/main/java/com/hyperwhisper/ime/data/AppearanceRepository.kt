package com.hyperwhisper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(name = "hyperwhisper_appearance")

/**
 * Repository for managing appearance and UI settings
 * Handles theme, colors, fonts, display preferences, and feature toggles
 */
@Singleton
class AppearanceRepository @Inject constructor(
    private val context: Context
) {
    private val dataStore = context.appearanceDataStore

    companion object {
        private val APPEARANCE_COLOR_SCHEME_KEY = stringPreferencesKey("appearance_color_scheme")
        private val APPEARANCE_USE_DYNAMIC_COLOR_KEY = booleanPreferencesKey("appearance_use_dynamic_color")
        private val APPEARANCE_DARK_MODE_KEY = stringPreferencesKey("appearance_dark_mode")
        private val APPEARANCE_UI_LANGUAGE_KEY = stringPreferencesKey("appearance_ui_language")
        private val APPEARANCE_UI_SCALE_KEY = stringPreferencesKey("appearance_ui_scale")
        private val APPEARANCE_FONT_FAMILY_KEY = stringPreferencesKey("appearance_font_family")
        private val APPEARANCE_AUTO_COPY_KEY = booleanPreferencesKey("appearance_auto_copy")
        private val APPEARANCE_ENABLE_HISTORY_KEY = booleanPreferencesKey("appearance_enable_history")
        private val APPEARANCE_TECHIE_MODE_KEY = booleanPreferencesKey("appearance_techie_mode")
        private val APPEARANCE_SHOW_KEYBOARD_SWITCHER_KEY = booleanPreferencesKey("appearance_show_keyboard_switcher")
        private val APPEARANCE_SAVE_ORIGINAL_AUDIO_FILES_KEY = booleanPreferencesKey("appearance_save_original_audio_files")
        private val APPEARANCE_MAX_HISTORY_ITEMS_KEY = stringPreferencesKey("appearance_max_history_items")
        private val APPEARANCE_UNLIMITED_HISTORY_KEY = booleanPreferencesKey("appearance_unlimited_history")
    }

    /**
     * Appearance Settings Flow
     * Emits current appearance configuration with safe fallbacks for enum parsing
     */
    val appearanceSettings: Flow<AppearanceSettings> = dataStore.data.map { preferences ->
        AppearanceSettings(
            colorScheme = preferences[APPEARANCE_COLOR_SCHEME_KEY]?.let {
                try {
                    ColorSchemeOption.valueOf(it)
                } catch (e: Exception) {
                    ColorSchemeOption.OCEAN_DEEP
                }
            } ?: ColorSchemeOption.OCEAN_DEEP,
            useDynamicColor = preferences[APPEARANCE_USE_DYNAMIC_COLOR_KEY] ?: true,
            darkModePreference = preferences[APPEARANCE_DARK_MODE_KEY]?.let {
                try {
                    DarkModePreference.valueOf(it)
                } catch (e: Exception) {
                    DarkModePreference.SYSTEM
                }
            } ?: DarkModePreference.SYSTEM,
            uiLanguage = preferences[APPEARANCE_UI_LANGUAGE_KEY] ?: "en",
            uiScale = preferences[APPEARANCE_UI_SCALE_KEY]?.let {
                try {
                    UIScaleOption.valueOf(it)
                } catch (e: Exception) {
                    UIScaleOption.MEDIUM
                }
            } ?: UIScaleOption.MEDIUM,
            fontFamily = preferences[APPEARANCE_FONT_FAMILY_KEY]?.let {
                try {
                    FontFamilyOption.valueOf(it)
                } catch (e: Exception) {
                    FontFamilyOption.DEFAULT
                }
            } ?: FontFamilyOption.DEFAULT,
            autoCopyToClipboard = preferences[APPEARANCE_AUTO_COPY_KEY] ?: true,
            enableHistoryPanel = preferences[APPEARANCE_ENABLE_HISTORY_KEY] ?: true,
            techieModeEnabled = preferences[APPEARANCE_TECHIE_MODE_KEY] ?: false,
            showKeyboardSwitcher = preferences[APPEARANCE_SHOW_KEYBOARD_SWITCHER_KEY] ?: false,
            saveOriginalAudioFiles = preferences[APPEARANCE_SAVE_ORIGINAL_AUDIO_FILES_KEY] ?: false,
            maxHistoryItems = preferences[APPEARANCE_MAX_HISTORY_ITEMS_KEY]?.toIntOrNull() ?: 20,
            unlimitedHistory = preferences[APPEARANCE_UNLIMITED_HISTORY_KEY] ?: false
        )
    }

    /**
     * Save complete appearance settings configuration
     */
    suspend fun saveAppearanceSettings(settings: AppearanceSettings) {
        dataStore.edit { preferences ->
            preferences[APPEARANCE_COLOR_SCHEME_KEY] = settings.colorScheme.name
            preferences[APPEARANCE_USE_DYNAMIC_COLOR_KEY] = settings.useDynamicColor
            preferences[APPEARANCE_DARK_MODE_KEY] = settings.darkModePreference.name
            preferences[APPEARANCE_UI_LANGUAGE_KEY] = settings.uiLanguage
            preferences[APPEARANCE_UI_SCALE_KEY] = settings.uiScale.name
            preferences[APPEARANCE_FONT_FAMILY_KEY] = settings.fontFamily.name
            preferences[APPEARANCE_AUTO_COPY_KEY] = settings.autoCopyToClipboard
            preferences[APPEARANCE_ENABLE_HISTORY_KEY] = settings.enableHistoryPanel
            preferences[APPEARANCE_TECHIE_MODE_KEY] = settings.techieModeEnabled
            preferences[APPEARANCE_SHOW_KEYBOARD_SWITCHER_KEY] = settings.showKeyboardSwitcher
            preferences[APPEARANCE_SAVE_ORIGINAL_AUDIO_FILES_KEY] = settings.saveOriginalAudioFiles
            preferences[APPEARANCE_MAX_HISTORY_ITEMS_KEY] = settings.maxHistoryItems.toString()
            preferences[APPEARANCE_UNLIMITED_HISTORY_KEY] = settings.unlimitedHistory
        }
    }
}
