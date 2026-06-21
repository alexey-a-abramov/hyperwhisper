package com.hyperwhisper.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * Appearance Settings
 */

// Color scheme options with environment themes
// All colors optimized for WCAG AA/AAA contrast ratios (4.5:1 minimum, 7:1 preferred)
// Ensures excellent readability on both light and dark backgrounds
enum class ColorSchemeOption(
    val displayName: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val tertiaryColor: Color
) {
    // Classic themes with improved contrast
    TERMINAL_DARK("Terminal Dark", Color(0xFF2C2C2C), Color(0xFF00D000), Color(0xFF0099FF)),
    OCEAN_DEEP("Ocean Deep", Color(0xFF005577), Color(0xFF0099CC), Color(0xFF66CCFF)),
    FOREST_NIGHT("Forest Night", Color(0xFF1B4D0E), Color(0xFF2E7D1E), Color(0xFF5CB85C)),
    SUNSET_HORIZON("Sunset Horizon", Color(0xFFCC5500), Color(0xFFDD7700), Color(0xFFFF9933)),
    ARCTIC_FROST("Arctic Frost", Color(0xFF3380AA), Color(0xFF5599CC), Color(0xFF88BBEE)),
    DESERT_STORM("Desert Storm", Color(0xFFAA8855), Color(0xFFCC9966), Color(0xFFDDBB88)),
    NEON_CITY("Neon City", Color(0xFFCC00CC), Color(0xFF00CCCC), Color(0xFFCCCC00)),
    CHERRY_BLOSSOM("Cherry Blossom", Color(0xFFDD88AA), Color(0xFFEE99BB), Color(0xFFFFAACC)),
    MIDNIGHT_SKY("Midnight Sky", Color(0xFF1A1A5C), Color(0xFF3B3B82), Color(0xFF5555AA)),
    LAVA_FLOW("Lava Flow", Color(0xFFCC3300), Color(0xFFDD4422), Color(0xFFFF6633)),
    MISTY_MOUNTAIN("Misty Mountain", Color(0xFF4A5555), Color(0xFF6A7777), Color(0xFF8A9999)),
    AUTUMN_LEAVES("Autumn Leaves", Color(0xFF773300), Color(0xFF994422), Color(0xFFCC6633)),

    // New professional themes with superior accessibility
    PROFESSIONAL_BLUE("Professional Blue", Color(0xFF0052CC), Color(0xFF2684FF), Color(0xFF4C9AFF)),
    WARM_EARTH("Warm Earth", Color(0xFF8B4513), Color(0xFFCD853F), Color(0xFFDEB887)),
    COOL_SLATE("Cool Slate", Color(0xFF2F4F4F), Color(0xFF556B2F), Color(0xFF708090)),
    VIBRANT_PURPLE("Vibrant Purple", Color(0xFF6A0DAD), Color(0xFF9932CC), Color(0xFFBA55D3)),
    EMERALD_GREEN("Emerald Green", Color(0xFF046307), Color(0xFF228B22), Color(0xFF32CD32)),
    RUBY_RED("Ruby Red", Color(0xFF8B0000), Color(0xFFDC143C), Color(0xFFFF6347));

    // For backwards compatibility with existing Material3 dynamic theming
    val seedColor: Color get() = primaryColor
}

// UI scale options
enum class UIScaleOption(val displayName: String, val scale: Float) {
    VERY_SMALL("Very Small", 0.85f),
    SMALL("Small", 0.92f),
    MEDIUM("Medium", 1.0f),
    LARGE("Large", 1.15f),
    VERY_LARGE("Very Large", 1.3f)
}

// Font family options
enum class FontFamilyOption(val displayName: String, val fontFamily: FontFamily) {
    DEFAULT("Default", FontFamily.Default),
    SERIF("Serif", FontFamily.Serif),
    SANS_SERIF("Sans Serif", FontFamily.SansSerif),
    MONOSPACE("Monospace", FontFamily.Monospace),
    CURSIVE("Cursive", FontFamily.Cursive)
}

// Dark mode preference options
enum class DarkModePreference(val displayName: String) {
    SYSTEM("Follow System"),
    LIGHT("Always Light"),
    DARK("Always Dark")
}

// Keyboard input mode options
//
// 4-mode design (post v1.267): DICTATION + QWERTY + CODE + EMOJI.
//
// SPECIAL_CHARS / SYSTEM_KEYS / VIBE_CODING / NUMPAD are kept as enum values
// for backward-compatibility with serialized settings. The render layer maps
// them onto CODE via [normalize], so existing user prefs migrate transparently
// without forcing a one-shot DataStore migration.
enum class KeyboardInputMode(val displayName: String) {
    DICTATION("Voice"),
    QWERTY("Text"),
    CODE("Code"),
    EMOJI("Emoji"),

    // Coding-agent quick-command modes. Hidden by default; user enables
    // individual agents in settings. Each renders the same AgentKeyboard
    // composable parameterized by [com.hyperwhisper.data.AgentCommands].
    AGENT_CLAUDE_CODE("Claude Code"),
    AGENT_OPENCODE("OpenCode"),
    AGENT_GEMINI("Gemini CLI"),
    AGENT_CODEX("Codex CLI"),
    // Cross-agent macro palette — hardcoded prompt phrases the user reuses
    // regardless of which CLI is on the other end. Trial; may consolidate or
    // promote to a category-within-agent if it proves too much of a switch tax.
    AGENT_MACROS("Text Snippets"),

    // --- Experimental layouts. Surfaced via the preset slot (long-press to
    // rebind) but kept out of the default swipe-cycle so they don't crowd
    // the daily-driver modes for users who haven't opted in.
    /**
     * Terminal-control keyboard. Each chip commits literal ASCII control
     * bytes (Ctrl+C = 0x03, Ctrl+L = 0x0C, …) and xterm escape sequences
     * (arrows, Home/End/PgUp/PgDn, Alt+B/F/Backspace). Bypasses the Android
     * key-chord path entirely, which is the only reliable way to send
     * Ctrl+X chords into Termux's PTY.
     */
    EXPERIMENTAL_TERMINAL("Terminal"),

    // --- Legacy values, transparently rerouted. Kept so old DataStore values
    // still parse. ---
    SPECIAL_CHARS("Symbols"),
    SYSTEM_KEYS("System Keys"),
    VIBE_CODING("Vibe Coding"),
    NUMPAD("Numpad");

    val isAgent: Boolean get() = this in agentModes
    val isExperimental: Boolean get() = this in experimentalModes

    companion object {
        val agentModes: Set<KeyboardInputMode> = setOf(
            AGENT_CLAUDE_CODE, AGENT_OPENCODE, AGENT_GEMINI, AGENT_CODEX, AGENT_MACROS
        )

        /** In-progress layouts surfaced via the preset slot only. */
        val experimentalModes: Set<KeyboardInputMode> = setOf(EXPERIMENTAL_TERMINAL)
    }

    /**
     * Normalize a stored or in-flight mode to one of the 4 modes the new UI
     * actually exposes. SPECIAL_CHARS folds back into QWERTY (it was the
     * shifted layer); the rest collapse into CODE.
     */
    fun normalize(): KeyboardInputMode = when (this) {
        SPECIAL_CHARS -> QWERTY
        SYSTEM_KEYS, VIBE_CODING, NUMPAD -> CODE
        else -> this
    }
}

// Keyboard layout options (language-specific).
//
// A "locality" unifies two concepts under one pick: the on-screen typing
// layout AND the speech-input language ([inputLanguageCode], ISO-639-1).
// Selecting a locality sets both, so "switch to Russian" flips the ЙЦУКЕН
// keys and points dictation at Russian in a single tap.
enum class KeyboardLayout(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val inputLanguageCode: String
) {
    ENGLISH("EN", "English", "English", "en"),
    RUSSIAN("RU", "Russian", "Русский", "ru"),
    SPANISH("ES", "Spanish", "Español", "es"),
    FRENCH("FR", "French", "Français", "fr"),
    GERMAN("DE", "German", "Deutsch", "de"),
    SWEDISH("SV", "Swedish", "Svenska", "sv"),
    ITALIAN("IT", "Italian", "Italiano", "it"),
    PORTUGUESE("PT", "Portuguese", "Português", "pt"),
    UKRAINIAN("UK", "Ukrainian", "Українська", "uk"),
    TURKISH("TR", "Turkish", "Türkçe", "tr"),
    POLISH("PL", "Polish", "Polski", "pl"),
    ARABIC("AR", "Arabic", "العربية", "ar")
}

// Appearance settings data class
data class AppearanceSettings(
    val colorScheme: ColorSchemeOption = ColorSchemeOption.OCEAN_DEEP,
    val useDynamicColor: Boolean = true,
    val darkModePreference: DarkModePreference = DarkModePreference.SYSTEM,
    val uiLanguage: String = "en", // UI language code (en, ru, etc.)
    val uiScale: UIScaleOption = UIScaleOption.MEDIUM,
    val fontFamily: FontFamilyOption = FontFamilyOption.DEFAULT,
    val autoCopyToClipboard: Boolean = true, // Matches AppearanceRepository read-fallback (true); was false here, a silent drift.
    val enableHistoryPanel: Boolean = true,
    val techieModeEnabled: Boolean = false, // Show technical details like logs and field info
    /**
     * Set of coding-agent keyboard modes the user has enabled. Empty by
     * default (agent modes hidden). Keys are stored as [KeyboardInputMode]
     * names (string) for forward-compat across enum value changes.
     */
    val enabledAgentKeyboards: Set<String> = emptySet(),
    val showKeyboardSwitcher: Boolean = false, // Show keyboard switcher button on main screen
    val saveOriginalAudioFiles: Boolean = true, // Save audio files for playback/reprocessing from history
    // Streaming (long-form) dictation: transcribe chunks live during recording,
    // then run one LLM pass at stop. Off by default — opt-in for long dictations.
    val streamingDictation: Boolean = false,
    val maxHistoryItems: Int = 20, // Maximum number of history items to keep (0 = unlimited)
    val unlimitedHistory: Boolean = false, // If true, maxHistoryItems is ignored
    val lastKeyboardInputMode: KeyboardInputMode = KeyboardInputMode.DICTATION, // Remember last keyboard mode
    /**
     * Preset bound to the third (configurable) slot in the top-strip mode bar.
     * Tap on that slot switches to this mode; long-press lets the user rebind
     * it. Defaults to CODE — a common third-mode pick after Voice and QWERTY.
     */
    val presetKeyboardMode: KeyboardInputMode = KeyboardInputMode.CODE,
    val currentKeyboardLayout: KeyboardLayout = KeyboardLayout.ENGLISH, // Current active layout
    val enabledKeyboardLayouts: Set<KeyboardLayout> = setOf(KeyboardLayout.ENGLISH), // Enabled layouts (EN enabled by default)
    val recentEmojis: List<String> = emptyList(), // Last 10 recently used emojis
    val perAppLayoutMemoryEnabled: Boolean = true // Auto-restore last used layout per foreground app
)
