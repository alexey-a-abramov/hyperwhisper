package com.hyperwhisper.ui.util

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

/**
 * Press-and-hold auto-repeat. Fires [onTrigger] immediately on press, then
 * again every [repeatIntervalMs] after an [initialDelayMs] hold — standard
 * mobile-keyboard backspace cadence.
 *
 * Do not chain alongside Surface(onClick=…) / IconButton(onClick=…) on the
 * same element: those install their own gesture detector and will either
 * double-fire or swallow the press before this modifier sees it.
 */
fun Modifier.repeatOnHold(
    initialDelayMs: Long = 500L,
    repeatIntervalMs: Long = 50L,
    onTrigger: () -> Unit
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(initialDelayMs)
            while (isPressed) {
                onTrigger()
                delay(repeatIntervalMs)
            }
        }
    }
    pointerInput(Unit) {
        detectTapGestures(onPress = {
            isPressed = true
            onTrigger()
            tryAwaitRelease()
            isPressed = false
        })
    }
}
