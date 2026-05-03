package com.hyperwhisper.ime.keyboard

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks Ctrl / Alt / Shift state for the Code keyboard's sticky modifier
 * keys. Each modifier has two flags:
 *   - active: applies to the next single key press, then clears (one-shot).
 *   - locked: stays on across multiple key presses until tapped off.
 *
 * The IME service consumes [State.toMetaState] when committing text from
 * the Code keyboard — if any modifier is active, the keystroke is sent via
 * [android.view.inputmethod.InputConnection.sendKeyEvent] with proper meta
 * flags rather than plain commitText. Apps that honor InputConnection meta
 * (Termux, IDEs, terminal emulators, vim) see Ctrl-C / Ctrl-Z / etc. as
 * actual keychords. Apps that ignore meta state get the raw character.
 */
@Singleton
class ModifierKeyState @Inject constructor() {

    data class State(
        val ctrl: Boolean = false,
        val alt: Boolean = false,
        val shift: Boolean = false,
        val ctrlLocked: Boolean = false,
        val altLocked: Boolean = false,
        val shiftLocked: Boolean = false
    ) {
        fun anyActive(): Boolean = ctrl || alt || shift

        fun toMetaState(): Int {
            var m = 0
            if (ctrl) m = m or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            if (alt) m = m or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            if (shift) m = m or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            return m
        }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun current(): State = _state.value

    /** Tap-toggle Ctrl. Long-press elsewhere should call [lockCtrl]. */
    fun toggleCtrl() = _state.update {
        if (it.ctrlLocked) it.copy(ctrl = false, ctrlLocked = false)
        else it.copy(ctrl = !it.ctrl)
    }
    fun toggleAlt() = _state.update {
        if (it.altLocked) it.copy(alt = false, altLocked = false)
        else it.copy(alt = !it.alt)
    }
    fun toggleShift() = _state.update {
        if (it.shiftLocked) it.copy(shift = false, shiftLocked = false)
        else it.copy(shift = !it.shift)
    }

    fun lockCtrl() = _state.update { it.copy(ctrl = true, ctrlLocked = true) }
    fun lockAlt() = _state.update { it.copy(alt = true, altLocked = true) }
    fun lockShift() = _state.update { it.copy(shift = true, shiftLocked = true) }

    /** Drop one-shot modifiers; preserve locked. Called after each key dispatch. */
    fun consumeOneShot() = _state.update {
        it.copy(
            ctrl = it.ctrlLocked,
            alt = it.altLocked,
            shift = it.shiftLocked
        )
    }

    fun clearAll() = _state.update { State() }
}

/**
 * Map a printable character to an Android keycode for [KeyEvent]. Returns
 * null for characters with no canonical keycode — caller should fall back
 * to commitText. Covers letters, digits, and the common shell punctuation.
 */
internal fun charToKeyCode(c: Char): Int? {
    val lc = c.lowercaseChar()
    return when (lc) {
        in 'a'..'z' -> KeyEvent.KEYCODE_A + (lc - 'a')
        in '0'..'9' -> KeyEvent.KEYCODE_0 + (lc - '0')
        ' ' -> KeyEvent.KEYCODE_SPACE
        '\n' -> KeyEvent.KEYCODE_ENTER
        '\t' -> KeyEvent.KEYCODE_TAB
        '.' -> KeyEvent.KEYCODE_PERIOD
        ',' -> KeyEvent.KEYCODE_COMMA
        '/' -> KeyEvent.KEYCODE_SLASH
        '\\' -> KeyEvent.KEYCODE_BACKSLASH
        '`' -> KeyEvent.KEYCODE_GRAVE
        '-' -> KeyEvent.KEYCODE_MINUS
        '=' -> KeyEvent.KEYCODE_EQUALS
        ';' -> KeyEvent.KEYCODE_SEMICOLON
        '\'' -> KeyEvent.KEYCODE_APOSTROPHE
        '[' -> KeyEvent.KEYCODE_LEFT_BRACKET
        ']' -> KeyEvent.KEYCODE_RIGHT_BRACKET
        else -> null
    }
}
