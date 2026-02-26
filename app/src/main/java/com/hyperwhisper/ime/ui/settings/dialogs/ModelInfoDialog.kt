package com.hyperwhisper.ui.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ApiProvider

@Composable
fun ModelInfoDialog(
    provider: ApiProvider,
    modelId: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Model Information")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Provider name
                Text(
                    text = "Provider: ${provider.displayName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Divider()

                // Current model
                Text(
                    text = "Selected Model:",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = modelId,
                    fontSize = 15.sp
                )

                Divider()

                // Provider-specific information
                when (provider) {
                    ApiProvider.OPENAI -> {
                        Text("OpenAI Whisper", fontWeight = FontWeight.Medium)
                        Text("• Multi-language support (98+ languages)", fontSize = 14.sp)
                        Text("• Translation to English", fontSize = 14.sp)
                        Text("• Word-level timestamps", fontSize = 14.sp)
                        Text("• Max file size: 25 MB", fontSize = 14.sp)
                        Text("• Supports: mp3, mp4, m4a, wav, webm", fontSize = 14.sp)
                    }
                    ApiProvider.DEEPGRAM -> {
                        Text("Deepgram Nova", fontWeight = FontWeight.Medium)
                        Text("• Extremely low latency", fontSize = 14.sp)
                        Text("• Speaker diarization", fontSize = 14.sp)
                        Text("• Smart formatting (punctuation)", fontSize = 14.sp)
                        Text("• Topic detection", fontSize = 14.sp)
                        Text("• Real-time & Batch processing", fontSize = 14.sp)
                    }
                    ApiProvider.ASSEMBLYAI -> {
                        Text("AssemblyAI Universal", fontWeight = FontWeight.Medium)
                        Text("• Speaker diarization", fontSize = 14.sp)
                        Text("• Audio intelligence features", fontSize = 14.sp)
                        Text("• PII Redaction", fontSize = 14.sp)
                        Text("• Auto-language detection", fontSize = 14.sp)
                        Text("• Async workflow", fontSize = 14.sp)
                    }
                    ApiProvider.GOOGLE_CLOUD -> {
                        Text("Google Cloud Speech", fontWeight = FontWeight.Medium)
                        Text("• Chirp Universal Speech Model", fontSize = 14.sp)
                        Text("• Domain-specific models", fontSize = 14.sp)
                        Text("• Profanity filtering", fontSize = 14.sp)
                        Text("• Automatic punctuation", fontSize = 14.sp)
                        Text("• Noise robustness", fontSize = 14.sp)
                    }
                    ApiProvider.AWS_TRANSCRIBE -> {
                        Text("AWS Transcribe", fontWeight = FontWeight.Medium)
                        Text("• Custom vocabulary", fontSize = 14.sp)
                        Text("• Vocabulary filtering", fontSize = 14.sp)
                        Text("• Speaker identification", fontSize = 14.sp)
                        Text("• Channel identification", fontSize = 14.sp)
                        Text("• Medical & Standard models", fontSize = 14.sp)
                    }
                    ApiProvider.AZURE_SPEECH -> {
                        Text("Azure AI Speech", fontWeight = FontWeight.Medium)
                        Text("• Custom Speech training", fontSize = 14.sp)
                        Text("• Pronunciation assessment", fontSize = 14.sp)
                        Text("• Phrase lists (dynamic grammar)", fontSize = 14.sp)
                        Text("• Silent pause support", fontSize = 14.sp)
                        Text("• Fast/Batch modes", fontSize = 14.sp)
                    }
                    ApiProvider.REVAI -> {
                        Text("Rev.ai", fontWeight = FontWeight.Medium)
                        Text("• High accuracy on accents", fontSize = 14.sp)
                        Text("• Human transcription fallback", fontSize = 14.sp)
                        Text("• Speaker identification", fontSize = 14.sp)
                        Text("• Custom vocabularies", fontSize = 14.sp)
                        Text("• Async workflow", fontSize = 14.sp)
                    }
                    ApiProvider.GROQ -> {
                        Text("Groq Whisper", fontWeight = FontWeight.Medium)
                        Text("• Ultra-fast inference", fontSize = 14.sp)
                        Text("• Whisper large-v3 models", fontSize = 14.sp)
                        Text("• Distil-whisper (faster)", fontSize = 14.sp)
                        Text("• Multi-language support", fontSize = 14.sp)
                        Text("• OpenAI-compatible API", fontSize = 14.sp)
                    }
                    ApiProvider.OPENROUTER -> {
                        Text("OpenRouter", fontWeight = FontWeight.Medium)
                        Text("• Access to multiple models", fontSize = 14.sp)
                        Text("• Unified API interface", fontSize = 14.sp)
                        Text("• Pay-per-use pricing", fontSize = 14.sp)
                        Text("• No subscriptions", fontSize = 14.sp)
                    }
                    ApiProvider.GEMINI -> {
                        Text("Google Gemini", fontWeight = FontWeight.Medium)
                        Text("• Multimodal AI model", fontSize = 14.sp)
                        Text("• Audio + text processing", fontSize = 14.sp)
                        Text("• Context understanding", fontSize = 14.sp)
                        Text("• Latest 2.0 Flash model", fontSize = 14.sp)
                    }
                    ApiProvider.ANTIGRAVITY -> {
                        Text("Google Antigravity (OAuth)", fontWeight = FontWeight.Medium)
                        Text("• OAuth-backed quota usage", fontSize = 14.sp)
                        Text("• OpenAI-compatible endpoint", fontSize = 14.sp)
                        Text("• API key typically not required", fontSize = 14.sp)
                        Text("• Chat-completion audio workflow", fontSize = 14.sp)
                    }
                    ApiProvider.HUGGINGFACE -> {
                        Text("Hugging Face", fontWeight = FontWeight.Medium)
                        Text("• Open source models", fontSize = 14.sp)
                        Text("• Whisper variants", fontSize = 14.sp)
                        Text("• Free inference API", fontSize = 14.sp)
                        Text("• Community-driven", fontSize = 14.sp)
                    }
                    ApiProvider.SELFHOSTED_WHISPER -> {
                        Text("Self-hosted Whisper", fontWeight = FontWeight.Medium)
                        Text("• Your own infrastructure (free)", fontSize = 14.sp)
                        Text("• OpenAI-compatible API", fontSize = 14.sp)
                        Text("• Privacy-focused (local deployment)", fontSize = 14.sp)
                        Text("• Customizable base URL", fontSize = 14.sp)
                        Text("• Optional authentication", fontSize = 14.sp)
                    }
                }

                Divider()

                // Available models
                Text(
                    text = "Available Models:",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                provider.defaultModels.forEach { model ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (model == modelId) Icons.Default.CheckCircle else Icons.Default.Circle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (model == modelId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Text(
                            text = model,
                            fontSize = 14.sp,
                            fontWeight = if (model == modelId) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE")
            }
        }
    )
}
