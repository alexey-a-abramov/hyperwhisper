package com.hyperwhisper.service

import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Toast

/**
 * Encapsulates all InputConnection manipulation done by the IME:
 * cursor movement, KeyEvent dispatch, text commit/delete, clipboard insert.
 *
 * The service owns Android lifecycle and Compose hosting; this class owns
 * the "talk to the editor" surface. It's a plain class (not a Hilt
 * singleton) because its lifetime is bound to the service.
 *
 * `currentInputConnection` is read lazily on each call -- it can change
 * across `onStartInput` boundaries, so caching it would be wrong.
 */
internal class InputConnectionController(
    private val service: VoiceInputMethodService,
) {
    private val ic get() = service.currentInputConnection

    /**
     * Commit text to the current input field. If modifier keys (Ctrl/Alt/
     * Shift) are active and the text is a single character with a known
     * keycode, dispatch as a real KeyEvent so apps that honor InputConnection
     * meta state (Termux, vim, IDEs) see the keychord.
     */
    fun commitText(text: String) {
        if (!service.isInputViewShown) {
            Toast.makeText(
                service,
                "Transcription completed and saved to history. Open keyboard to insert manually.",
                Toast.LENGTH_SHORT
            ).show()
            Log.d(TAG, "Skipped commitText because input view is hidden")
            return
        }

        val ic = service.currentInputConnection ?: return
        try {
            // Modifier-aware path: when the user has Ctrl/Alt/Shift active on
            // the Code keyboard and the next text is a single character with
            // a known keycode, dispatch as a real KeyEvent so apps that honor
            // InputConnection meta state (Termux, vim, IDEs) see the keychord.
            // Multi-char strings or unknown chars fall back to commitText, but
            // the modifier flags still consume so the user sees them clear.
            val mods = service.modifierKeyState.current()
            if (mods.anyActive() && text.length == 1) {
                val keyCode = com.hyperwhisper.ime.keyboard.charToKeyCode(text[0])
                if (keyCode != null) {
                    val now = android.os.SystemClock.uptimeMillis()
                    val meta = mods.toMetaState()
                    ic.sendKeyEvent(
                        KeyEvent(
                            now, now, KeyEvent.ACTION_DOWN,
                            keyCode, 0, meta, 0, 0,
                            KeyEvent.FLAG_SOFT_KEYBOARD
                        )
                    )
                    ic.sendKeyEvent(
                        KeyEvent(
                            now, now, KeyEvent.ACTION_UP,
                            keyCode, 0, meta, 0, 0,
                            KeyEvent.FLAG_SOFT_KEYBOARD
                        )
                    )
                    Log.d(TAG, "Sent KeyEvent: keycode=$keyCode meta=$meta from text='$text'")
                    service.modifierKeyState.consumeOneShot()
                    return
                }
            }
            ic.beginBatchEdit()
            ic.commitText(text, 1)
            ic.endBatchEdit()
            if (mods.anyActive()) service.modifierKeyState.consumeOneShot()
            Log.d(TAG, "Committed text: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Error committing text", e)
        }
    }

    /**
     * Delete one character before the cursor.
     */
    fun deleteBackward() {
        val ic = ic ?: return
        try {
            // Use sendKeyEvent instead of deleteSurroundingText for better reliability
            // during rapid repeat operations. sendKeyEvent simulates actual key presses
            // and doesn't suffer from the batching issues that deleteSurroundingText has.
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            Log.d(TAG, "Deleted character")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting text", e)
        }
    }

    /**
     * Delete all text in the current input field.
     */
    fun deleteAll() {
        val ic = ic ?: return
        try {
            // Get text before and after cursor
            val textBefore = ic.getTextBeforeCursor(100000, 0)
            val textAfter = ic.getTextAfterCursor(100000, 0)

            val beforeLength = textBefore?.length ?: 0
            val afterLength = textAfter?.length ?: 0

            if (beforeLength > 0 || afterLength > 0) {
                ic.deleteSurroundingText(beforeLength, afterLength)
                Log.d(TAG, "Deleted all text (before: $beforeLength, after: $afterLength)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all text", e)
        }
    }

    /**
     * Delete selected text if any exists; otherwise delete one character.
     */
    fun deleteSelected() {
        val ic = ic ?: return
        try {
            val selectedText = ic.getSelectedText(0)
            if (!selectedText.isNullOrEmpty()) {
                ic.commitText("", 1) // This replaces selected text with empty string
                Log.d(TAG, "Deleted selected text: ${selectedText.take(50)}")
            } else {
                // If no selection, delete one character as fallback
                deleteBackward()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting selected text", e)
            // Fallback to normal delete
            deleteBackward()
        }
    }

    /**
     * Insert clipboard contents at cursor position.
     */
    fun insertClipboard() {
        try {
            val clipboard = service.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                if (text.isNotEmpty()) {
                    commitText(text)
                    Log.d(TAG, "Inserted clipboard text: ${text.take(50)}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting clipboard", e)
        }
    }

    /**
     * Handle enter key press, sending an action or a newline.
     */
    fun handleEnter() {
        val editorInfo = service.currentEditorInfo
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        val isMultiLine = editorInfo?.inputType?.and(InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0

        when {
            // Always insert newline in multi-line fields if no specific action is set
            isMultiLine && (action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) -> {
                commitText("\n")
                Log.d(TAG, "handleEnter: newline in multi-line")
            }
            // Perform the editor action if specified
            action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED -> {
                if (!service.sendDefaultEditorAction(true)) {
                    Log.w(TAG, "handleEnter: sendDefaultEditorAction failed, falling back to newline.")
                    commitText("\n")
                } else {
                    Log.d(TAG, "handleEnter: sent editor action $action")
                }
            }
            // Default to newline
            else -> {
                commitText("\n")
                Log.d(TAG, "handleEnter: newline default")
            }
        }
    }

    /**
     * Move cursor one position to the left.
     */
    fun moveCursorLeft() {
        val ic = ic ?: return
        try {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
        } catch (e: Exception) {
            Log.e(TAG, "Error moving cursor left", e)
        }
    }

    /**
     * Move cursor one position to the right.
     */
    fun moveCursorRight() {
        val ic = ic ?: return
        try {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
        } catch (e: Exception) {
            Log.e(TAG, "Error moving cursor right", e)
        }
    }

    /**
     * Move cursor one line up.
     */
    fun moveCursorUp() {
        val ic = ic ?: return
        try {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP))
        } catch (e: Exception) {
            Log.e(TAG, "Error moving cursor up", e)
        }
    }

    /**
     * Move cursor one line down.
     */
    fun moveCursorDown() {
        val ic = ic ?: return
        try {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN))
        } catch (e: Exception) {
            Log.e(TAG, "Error moving cursor down", e)
        }
    }

    /**
     * Page up.
     */
    fun pageUp() {
        val ic = ic ?: return
        try {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_UP))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_UP))
        } catch (e: Exception) {
            Log.e(TAG, "Error sending page up", e)
        }
    }

    /**
     * Page down.
     */
    fun pageDown() {
        val ic = ic ?: return
        try {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_DOWN))
        } catch (e: Exception) {
            Log.e(TAG, "Error sending page down", e)
        }
    }

    /**
     * Move cursor to beginning of line/field.
     */
    fun moveToHome() {
        val ic = ic ?: return
        try {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_HOME))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MOVE_HOME))
        } catch (e: Exception) {
            Log.e(TAG, "Error moving to home", e)
        }
    }

    /**
     * Move cursor to end of line/field.
     */
    fun moveToEnd() {
        val ic = ic ?: return
        try {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_END))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MOVE_END))
        } catch (e: Exception) {
            Log.e(TAG, "Error moving to end", e)
        }
    }

    /**
     * Send Insert key.
     */
    fun sendInsert() {
        val ic = ic ?: return
        try {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_INSERT))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_INSERT))
        } catch (e: Exception) {
            Log.e(TAG, "Error sending insert", e)
        }
    }

    /**
     * Send forward delete (Delete key, not Backspace).
     */
    fun sendForwardDelete() {
        val ic = ic ?: return
        try {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_FORWARD_DEL))
        } catch (e: Exception) {
            Log.e(TAG, "Error sending forward delete", e)
        }
    }

    /**
     * Send Escape key.
     */
    fun sendEscape() {
        val ic = ic ?: return
        try {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE))
        } catch (e: Exception) {
            Log.e(TAG, "Error sending escape", e)
        }
    }

    /**
     * Send Tab key.
     */
    fun sendTab() {
        val ic = ic ?: return
        try {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB))
        } catch (e: Exception) {
            Log.e(TAG, "Error sending tab", e)
        }
    }

    /**
     * Dispatch an arbitrary key chord (keycode + meta-state mask) to the
     * focused app. Available for agent-keyboard chips that need to fire a
     * real modifier+key combo against a standard Android receiver.
     *
     * Note: terminal emulators (Termux) ignore IME meta-state on KeyEvents
     * and only consume committed text into the PTY. Chips targeting a TUI
     * (Claude Code's mode cycler, vim, etc.) should commit the equivalent
     * xterm escape sequence as plain text instead — see the Plan / Auto
     * chip in AgentCommands.kt for the pattern.
     *
     * Uses [KeyEvent.FLAG_SOFT_KEYBOARD] so receivers can tell this came
     * from an IME, mirroring what [commitText] does for modifier-aware
     * single-char dispatch.
     */
    fun sendKeyChord(keyCode: Int, metaState: Int = 0) {
        val ic = ic ?: return
        try {
            val now = android.os.SystemClock.uptimeMillis()
            ic.sendKeyEvent(
                KeyEvent(
                    now, now, KeyEvent.ACTION_DOWN,
                    keyCode, 0, metaState, 0, 0,
                    KeyEvent.FLAG_SOFT_KEYBOARD
                )
            )
            ic.sendKeyEvent(
                KeyEvent(
                    now, now, KeyEvent.ACTION_UP,
                    keyCode, 0, metaState, 0, 0,
                    KeyEvent.FLAG_SOFT_KEYBOARD
                )
            )
            Log.d(TAG, "Sent key chord: keycode=$keyCode meta=$metaState")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending key chord", e)
        }
    }

    private companion object {
        private const val TAG = "VoiceIME"
    }
}
