package com.hyperwhisper.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.RecordingState
import com.hyperwhisper.data.VoiceMode

@Composable
fun KeyboardScreen(
    viewModel: KeyboardViewModel,
    onTextCommit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recordingState by viewModel.recordingState.collectAsState()
    val transcribedText by viewModel.transcribedText.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val voiceModes by viewModel.voiceModes.collectAsState()
    val selectedModeId by viewModel.selectedModeId.collectAsState()

    // Auto-commit transcribed text
    LaunchedEffect(transcribedText) {
        if (transcribedText.isNotEmpty()) {
            onTextCommit(transcribedText)
            viewModel.clearTranscribedText()
        }
    }

    // DON'T auto-clear errors - let user read them
    // Errors will be cleared when user taps mic again or manually dismisses

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Row: Mode Selector + Settings Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeSelector(
                    modes = voiceModes,
                    selectedModeId = selectedModeId,
                    onModeSelected = { viewModel.selectMode(it) },
                    enabled = recordingState == RecordingState.IDLE,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        val intent = Intent(context, com.hyperwhisper.ui.settings.SettingsActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Center: Microphone Button
            MicrophoneButton(
                recordingState = recordingState,
                onStartRecording = { viewModel.startRecording() },
                onStopRecording = { viewModel.stopRecording() },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom: Status Text or Error
            StatusText(
                recordingState = recordingState,
                errorMessage = errorMessage,
                onDismissError = { viewModel.clearError() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelector(
    modes: List<VoiceMode>,
    selectedModeId: String,
    onModeSelected: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedMode = modes.firstOrNull { it.id == selectedModeId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedMode?.name ?: "Select Mode",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            modes.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.name) },
                    onClick = {
                        onModeSelected(mode.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun MicrophoneButton(
    recordingState: RecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        when (recordingState) {
            RecordingState.IDLE -> {
                IdleMicButton(onClick = onStartRecording)
            }
            RecordingState.RECORDING -> {
                RecordingMicButton(onClick = onStopRecording)
            }
            RecordingState.PROCESSING -> {
                ProcessingIndicator()
            }
            RecordingState.ERROR -> {
                IdleMicButton(onClick = onStartRecording)
            }
        }
    }
}

@Composable
fun IdleMicButton(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(100.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = Color.White
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Start Recording",
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
fun RecordingMicButton(onClick: () -> Unit) {
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

    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .size(100.dp)
            .scale(scale),
        containerColor = Color(0xFFE53935), // Red
        contentColor = Color.White
    ) {
        Icon(
            imageVector = Icons.Default.Stop,
            contentDescription = "Stop Recording",
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
fun ProcessingIndicator() {
    Box(
        modifier = Modifier.size(100.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            strokeWidth = 6.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun StatusText(
    recordingState: RecordingState,
    errorMessage: String?,
    onDismissError: () -> Unit
) {
    val context = LocalContext.current

    // Show error in a prominent card if present
    if (errorMessage != null) {
        val isPermissionError = errorMessage.contains("permission", ignoreCase = true)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = errorMessage,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isPermissionError) {
                        Button(
                            onClick = {
                                // Open app settings
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                contentColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "OPEN SETTINGS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onDismissError,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text(
                            "DISMISS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    } else {
        // Normal status text
        val text = when (recordingState) {
            RecordingState.RECORDING -> "Recording... (tap to stop)"
            RecordingState.PROCESSING -> "Processing audio..."
            RecordingState.IDLE -> "Tap microphone to start recording"
            RecordingState.ERROR -> "Error occurred"
        }

        val color = when (recordingState) {
            RecordingState.RECORDING -> Color(0xFFE53935)
            RecordingState.PROCESSING -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        }

        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = if (recordingState != RecordingState.IDLE) FontWeight.Medium else FontWeight.Normal,
            color = color
        )
    }
}
