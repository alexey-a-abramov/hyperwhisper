package com.hyperwhisper.ui.buttons

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gesture detector for microphone button
 * Handles: single tap, double-tap, long press, press and hold
 */
@Composable
fun Modifier.micGestureDetector(
    onSingleTap: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onPressStart: () -> Unit = {},
    onPressRelease: () -> Unit = {}
): Modifier {
    val coroutineScope = rememberCoroutineScope()
    var lastTapTime by remember { mutableStateOf(0L) }
    var tapCount by remember { mutableStateOf(0) }

    val doubleTapTimeoutMs = 400L
    val longPressTimeoutMs = 500L

    return this.pointerInput(Unit) {
        detectTapGestures(
            onPress = {
                onPressStart()
                val pressed = try {
                    tryAwaitRelease()
                    true
                } finally {
                    onPressRelease()
                }
            },
            onTap = {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastTap = currentTime - lastTapTime

                if (timeSinceLastTap < doubleTapTimeoutMs) {
                    // Double tap detected
                    tapCount = 0
                    lastTapTime = 0
                    onDoubleTap()
                } else {
                    // Start counting taps
                    tapCount = 1
                    lastTapTime = currentTime

                    // Wait to see if there's a second tap
                    coroutineScope.launch {
                        delay(doubleTapTimeoutMs)
                        if (tapCount == 1 && System.currentTimeMillis() - lastTapTime >= doubleTapTimeoutMs) {
                            tapCount = 0
                            onSingleTap()
                        }
                    }
                }
            },
            onLongPress = {
                onLongPress()
            }
        )
    }
}
