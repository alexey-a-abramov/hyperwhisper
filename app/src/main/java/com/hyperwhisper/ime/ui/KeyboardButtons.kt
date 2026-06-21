package com.hyperwhisper.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.ui.util.repeatOnHold
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal enum class KeyboardActionStyle {
    NORMAL,
    SPACE,
    BACKSPACE,
    ENTER
}

private val KeyboardSpecialTextColor = Color(0xFF000000)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun KeyboardKeyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 36.dp,
    /**
     * If non-null, holding this key inserts [longPressLabel] instead of [label].
     * Surfaced as a small superscript hint on the key. Used for QWERTY's
     * number row (1→!, 2→@, etc.) and any future Gboard-style chord keys.
     */
    longPressLabel: String? = null,
    onLongPress: (() -> Unit)? = null
) {
    // Press feedback: drive the fill straight off the interaction source so the
    // key under the finger lights up (the default ripple is nearly invisible on
    // a white cap). The thin border gives every key a crisp, divided edge.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val baseModifier = modifier
        .height(height)
        .clip(RoundedCornerShape(8.dp))
        .background(if (pressed) KeyboardKeyPressedColor else KeyboardKeyColor)
        .border(1.dp, KeyboardKeyBorderColor, RoundedCornerShape(8.dp))
    val tappableMod = if (onLongPress != null) {
        baseModifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
            onLongClick = onLongPress
        )
    } else {
        baseModifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    }
    Box(
        modifier = tappableMod,
        contentAlignment = Alignment.Center
    ) {
        if (longPressLabel != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = longPressLabel,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Normal,
                    color = KeyboardKeyTextColor.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 4.dp)
                )
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = KeyboardKeyTextColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = KeyboardKeyTextColor
            )
        }
    }
}

@Composable
internal fun RepeatingActionButton(
    onAction: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    label: String? = null,
    style: KeyboardActionStyle = KeyboardActionStyle.NORMAL,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp? = null,
    initialDelayMs: Long = 500L,
    repeatDelayMs: Long = 50L
) {
    require(icon != null || label != null) { "RepeatingActionButton needs icon or label" }

    val backgroundColor = when (style) {
        KeyboardActionStyle.NORMAL -> KeyboardKeyColor
        KeyboardActionStyle.SPACE -> KeyboardSpaceColor
        KeyboardActionStyle.BACKSPACE -> KeyboardBackspaceColor
        KeyboardActionStyle.ENTER -> KeyboardEnterColor
    }
    val contentColor = when (style) {
        KeyboardActionStyle.NORMAL -> KeyboardKeyTextColor
        else -> KeyboardSpecialTextColor
    }

    val sized = if (height != null) modifier.height(height) else modifier
    Surface(
        modifier = sized.repeatOnHold(
            initialDelayMs = initialDelayMs,
            startIntervalMs = repeatDelayMs,
            onTrigger = onAction
        ),
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label ?: "repeating action",
                    tint = contentColor
                )
            } else {
                Text(
                    text = label!!,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
internal fun LongPressActionButton(
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    style: KeyboardActionStyle = KeyboardActionStyle.NORMAL,
    height: androidx.compose.ui.unit.Dp = 42.dp,
    longPressThreshold: Long = 800L
) {
    var pressStartTime by remember { mutableStateOf(0L) }
    var isLongPressTriggered by remember { mutableStateOf(false) }

    val backgroundColor = when (style) {
        KeyboardActionStyle.NORMAL -> KeyboardKeyColor
        KeyboardActionStyle.SPACE -> KeyboardSpaceColor
        KeyboardActionStyle.BACKSPACE -> KeyboardBackspaceColor
        KeyboardActionStyle.ENTER -> KeyboardEnterColor
    }
    val contentColor = when (style) {
        KeyboardActionStyle.NORMAL -> KeyboardKeyTextColor
        else -> KeyboardSpecialTextColor
    }

    Box(
        modifier = modifier
            .height(height)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressStartTime = System.currentTimeMillis()
                        isLongPressTriggered = false

                        // Wait for release or long press
                        val released = try {
                            withTimeout(longPressThreshold) {
                                tryAwaitRelease()
                                true
                            }
                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                            // Long press threshold reached
                            isLongPressTriggered = true
                            onLongPress()
                            tryAwaitRelease()
                            false
                        }

                        // If released before threshold, handle as click
                        if (released && !isLongPressTriggered) {
                            onClick()
                        }
                    }
                )
            }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(10.dp),
            color = backgroundColor,
            tonalElevation = 1.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label ?: "action",
                        tint = contentColor
                    )
                } else if (label != null) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
internal fun KeyboardActionButton(
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KeyboardActionStyle = KeyboardActionStyle.NORMAL,
    height: androidx.compose.ui.unit.Dp = 42.dp
) {
    val backgroundColor = when (style) {
        KeyboardActionStyle.NORMAL -> KeyboardKeyColor
        KeyboardActionStyle.SPACE -> KeyboardSpaceColor
        KeyboardActionStyle.BACKSPACE -> KeyboardBackspaceColor
        KeyboardActionStyle.ENTER -> KeyboardEnterColor
    }
    val contentColor = when (style) {
        KeyboardActionStyle.NORMAL -> KeyboardKeyTextColor
        else -> KeyboardSpecialTextColor
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label ?: "action",
                    tint = contentColor
                )
            } else if (label != null) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor
                )
            }
        }
    }
}
