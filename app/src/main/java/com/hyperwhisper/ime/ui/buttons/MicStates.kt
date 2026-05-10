package com.hyperwhisper.ui.buttons

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ProcessingPhase

@Composable
fun IdleMicButton(
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onPressStart: () -> Unit = {},
    onPressRelease: () -> Unit = {},
    walkieTalkieMode: Boolean = false
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .size(72.dp)
            .micGestureDetector(
                onSingleTap = if (walkieTalkieMode) ({}) else onClick,
                onDoubleTap = onDoubleTap,
                onLongPress = onLongPress,
                onPressStart = if (walkieTalkieMode) onPressStart else ({}),
                onPressRelease = if (walkieTalkieMode) onPressRelease else ({})
            ),
        shape = if (walkieTalkieMode) CircleShape else RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = Color.White
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Start Recording",
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
fun ProcessingMicButton(
    processingPhase: ProcessingPhase = ProcessingPhase.PREPARING_AUDIO,
    onClick: () -> Unit = {}
) {
    // Different animations based on processing phase
    when (processingPhase) {
        ProcessingPhase.PREPARING_AUDIO -> {
            AnimatedProcessingButton(
                icon = Icons.Default.HourglassEmpty,
                color = MaterialTheme.colorScheme.tertiary,
                animationType = AnimationType.PULSE,
                onClick = onClick
            )
        }
        ProcessingPhase.SENDING_TO_SERVER -> {
            AnimatedProcessingButton(
                icon = Icons.Default.CloudUpload,
                color = Color(0xFF4CAF50), // Green
                animationType = AnimationType.PULSE_FAST,
                onClick = onClick
            )
        }
        ProcessingPhase.WAITING_FOR_RESPONSE -> {
            AnimatedProcessingButton(
                icon = Icons.Default.HourglassEmpty,
                color = Color(0xFFFF9800), // Orange
                animationType = AnimationType.ROTATE,
                onClick = onClick
            )
        }
        ProcessingPhase.RECEIVING_DATA -> {
            AnimatedProcessingButton(
                icon = Icons.Default.CheckCircle,
                color = Color(0xFF4CAF50), // Green
                animationType = AnimationType.PULSE,
                onClick = onClick
            )
        }
        ProcessingPhase.COMPLETE -> {
            AnimatedProcessingButton(
                icon = Icons.Default.CheckCircle,
                color = Color(0xFF4CAF50), // Green
                animationType = AnimationType.SCALE_ONCE,
                onClick = onClick
            )
        }
        ProcessingPhase.ERROR -> {
            AnimatedProcessingButton(
                icon = Icons.Default.Mic,
                color = MaterialTheme.colorScheme.error,
                animationType = AnimationType.NONE,
                onClick = onClick
            )
        }
        ProcessingPhase.IDLE -> {
            // Should not happen, but fallback to idle button
            IdleMicButton(onClick = onClick)
        }
    }
}

enum class AnimationType {
    NONE,
    PULSE,
    PULSE_FAST,
    ROTATE,
    SCALE_ONCE
}

@Composable
private fun AnimatedProcessingButton(
    icon: ImageVector,
    color: Color,
    animationType: AnimationType,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "processing")

    val scale by when (animationType) {
        AnimationType.PULSE -> infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        AnimationType.PULSE_FAST -> infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        else -> infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Restart
            ),
            label = "scale"
        )
    }

    val rotation by when (animationType) {
        AnimationType.ROTATE -> infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
        else -> infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
    }

    val alpha by when (animationType) {
        AnimationType.PULSE, AnimationType.PULSE_FAST -> infiniteTransition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
        else -> infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Restart
            ),
            label = "alpha"
        )
    }

    Box(
        modifier = Modifier.size(72.dp),
        contentAlignment = Alignment.Center
    ) {
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier
                .size(72.dp)
                .scale(scale)
                .alpha(alpha),
            shape = RoundedCornerShape(16.dp),
            containerColor = color,
            contentColor = Color.White
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Processing",
                modifier = Modifier
                    .size(36.dp)
                    .rotate(rotation)
            )
        }

        // Show a circular progress indicator around the button for some phases
        if (animationType == AnimationType.ROTATE || animationType == AnimationType.PULSE_FAST) {
            CircularProgressIndicator(
                modifier = Modifier.size(76.dp),
                strokeWidth = 2.dp,
                color = color.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun AwaitingConfirmationButton(
    recordingDuration: Long,
    onClick: () -> Unit
) {
    // Gentle pulsing animation to draw attention
    val infiniteTransition = rememberInfiniteTransition(label = "awaiting")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // Calculate minutes and seconds
    val seconds = (recordingDuration / 1000) % 60
    val minutes = (recordingDuration / 1000) / 60
    val timeText = "$minutes:${seconds.toString().padStart(2, '0')}"

    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .size(72.dp)
            .scale(scale)
            .alpha(alpha),
        shape = RoundedCornerShape(16.dp),
        containerColor = Color(0xFFFF9800), // Orange to indicate waiting
        contentColor = Color.White
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Confirm Recording",
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = timeText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun RecordingMicButton(
    onClick: () -> Unit,
    onDoubleTap: () -> Unit = {},
    recordingDuration: Long = 0L,
    walkieTalkieMode: Boolean = false
) {
    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Calculate minutes and seconds
    val seconds = (recordingDuration / 1000) % 60
    val minutes = (recordingDuration / 1000) / 60
    val timeText = "$minutes:${seconds.toString().padStart(2, '0')}"

    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .size(72.dp)
            .scale(scale)
            .micGestureDetector(
                onSingleTap = onClick,
                onDoubleTap = onDoubleTap
            ),
        shape = if (walkieTalkieMode) CircleShape else RoundedCornerShape(16.dp),
        containerColor = Color(0xFFE53935), // Red
        contentColor = Color.White
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop Recording",
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = timeText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
