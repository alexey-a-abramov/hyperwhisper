package com.hyperwhisper.ui.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ApiProvider

@Composable
fun ProviderKeyInstructionsDialog(
    provider: ApiProvider,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How to get API key") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Provider: ${provider.displayName}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Divider()
                instructionsFor(provider).forEach { line ->
                    Text(text = line, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE") }
        }
    )
}

private fun instructionsFor(provider: ApiProvider): List<String> = when (provider) {
    ApiProvider.OPENAI -> listOf(
        "1. Open: https://platform.openai.com/api-keys",
        "2. Click Create new secret key.",
        "3. Copy key (starts with sk-) and paste into API Key."
    )
    ApiProvider.DEEPGRAM -> listOf(
        "1. Open: https://console.deepgram.com/project",
        "2. Create/select project and open API Keys.",
        "3. Create key and paste it into API Key."
    )
    ApiProvider.ASSEMBLYAI -> listOf(
        "1. Open: https://www.assemblyai.com/dashboard",
        "2. Go to API Keys.",
        "3. Copy key and paste into API Key."
    )
    ApiProvider.GOOGLE_CLOUD -> listOf(
        "1. Open: https://console.cloud.google.com/",
        "2. Enable Speech-to-Text API for your project.",
        "3. Create credentials and paste key/token into API Key."
    )
    ApiProvider.AWS_TRANSCRIBE -> listOf(
        "1. Open AWS Console: https://console.aws.amazon.com/",
        "2. Create IAM access key with Transcribe permissions.",
        "3. Use your proxy/gateway key format in API Key field."
    )
    ApiProvider.AZURE_SPEECH -> listOf(
        "1. Open: https://portal.azure.com/",
        "2. Create Speech resource and open Keys and Endpoint.",
        "3. Copy key and set endpoint + API Key."
    )
    ApiProvider.REVAI -> listOf(
        "1. Open: https://www.rev.ai/auth/signup",
        "2. Create account and generate access token.",
        "3. Paste token into API Key."
    )
    ApiProvider.GROQ -> listOf(
        "1. Open: https://console.groq.com/keys",
        "2. Create API key.",
        "3. Paste into API Key."
    )
    ApiProvider.OPENROUTER -> listOf(
        "1. Open: https://openrouter.ai/keys",
        "2. Create key.",
        "3. Paste into API Key."
    )
    ApiProvider.GEMINI -> listOf(
        "1. Open: https://aistudio.google.com/app/apikey",
        "2. Create API key.",
        "3. Paste into API Key."
    )
    ApiProvider.HUGGINGFACE -> listOf(
        "1. Open: https://huggingface.co/settings/tokens",
        "2. Create access token.",
        "3. Paste token into API Key."
    )
    ApiProvider.SELFHOSTED_WHISPER -> listOf(
        "1. Deploy your own OpenAI-compatible Whisper server.",
        "2. Put server URL into Base URL.",
        "3. If auth enabled on server, paste token into API Key."
    )
}
