package com.hyperwhisper.ui.buttons

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Repeatable delete button with hold-to-clear functionality
 * - Tap: Delete one character
 * - Hold: Repeat delete every 50ms
 * - Hold 5s: Delete all text
 * Background color changes based on hold duration
 */
@Composable
fun RepeatableDeleteButton(
    onDelete: () -> Unit,
    onDeleteAll: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    var pressStartTime by remember { mutableStateOf(0L) }
    var hasTriggeredDeleteAll by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            pressStartTime = System.currentTimeMillis()
            hasTriggeredDeleteAll = false

            // Initial delay before repeat starts (500ms like typical keyboards)
            delay(500)

            // Repeat deletion while pressed (50ms between deletions for fast repeat)
            while (isPressed) {
                val pressDuration = System.currentTimeMillis() - pressStartTime

                // After 5 seconds of holding, delete all text
                if (pressDuration >= 5000 && !hasTriggeredDeleteAll) {
                    onDeleteAll()
                    hasTriggeredDeleteAll = true
                    // Stop repeating after delete all
                    break
                }

                onDelete()
                delay(50)
            }
        }
    }

    // Determine button color based on press duration
    val pressDuration = if (isPressed) {
        System.currentTimeMillis() - pressStartTime
    } else {
        0L
    }

    val backgroundColor = when {
        pressDuration >= 5000 -> MaterialTheme.colorScheme.error
        pressDuration >= 3000 -> MaterialTheme.colorScheme.errorContainer
        isPressed -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    }

    // Minimal circular button with left arrow icon
    Surface(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onDelete() // Immediate first delete
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        shape = CircleShape,
        color = backgroundColor,
        tonalElevation = if (isPressed) 8.dp else 2.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Backspace",
                tint = if (pressDuration >= 3000) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
