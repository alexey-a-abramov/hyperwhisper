package com.hyperwhisper.ui.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ApiProvider

@Composable
fun ProviderKeyInstructionsDialog(
    provider: ApiProvider,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

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
                    Text(text = line.text, fontSize = 14.sp)
                    line.url?.let { url ->
                        Text(
                            text = url,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { uriHandler.openUri(url) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE") }
        }
    )
}

private data class ProviderInstructionLine(
    val text: String,
    val url: String? = null
)

private fun instructionsFor(provider: ApiProvider): List<ProviderInstructionLine> = when (provider) {
    ApiProvider.OPENAI -> listOf(
        ProviderInstructionLine("1. Open this page:", "https://platform.openai.com/api-keys"),
        ProviderInstructionLine("2. Click Create new secret key."),
        ProviderInstructionLine("3. Copy key (starts with sk-) and paste into API Key.")
    )
    ApiProvider.DEEPGRAM -> listOf(
        ProviderInstructionLine("1. Open this page:", "https://console.deepgram.com/project"),
        ProviderInstructionLine("2. Create/select project and open API Keys."),
        ProviderInstructionLine("3. Create key and paste it into API Key.")
    )
    ApiProvider.ASSEMBLYAI -> listOf(
        ProviderInstructionLine("1. Open this page:", "https://www.assemblyai.com/dashboard"),
        ProviderInstructionLine("2. Go to API Keys."),
        ProviderInstructionLine("3. Copy key and paste into API Key.")
    )
    ApiProvider.GOOGLE_CLOUD -> listOf(
        ProviderInstructionLine("1. Open this page:", "https://console.cloud.google.com/"),
        ProviderInstructionLine("2. Enable Speech-to-Text API for your project."),
        ProviderInstructionLine("3. Create credentials and paste key/token into API Key.")
    )
    ApiProvider.AWS_TRANSCRIBE -> listOf(
        ProviderInstructionLine("1. Open AWS Console:", "https://console.aws.amazon.com/"),
        ProviderInstructionLine("2. Create IAM access key with Transcribe permissions."),
        ProviderInstructionLine("3. Use your proxy/gateway key format in API Key field.")
    )
    ApiProvider.AZURE_SPEECH -> listOf(
        ProviderInstructionLine("1. Open this page:", "https://portal.azure.com/"),
        ProviderInstructionLine("2. Create Speech resource and open Keys and Endpoint."),
        ProviderInstructionLine("3. Copy key and set endpoint + API Key.")
    )
    ApiProvider.DEEPSEEK -> listOf(
        ProviderInstructionLine("1. Open this page:", "https://platform.deepseek.com/api_keys"),
        ProviderInstructionLine("2. Create an API key."),
        ProviderInstructionLine("3. Paste the key into API Key.")
    )
    ApiProvider.MISTRAL -> listOf(
        ProviderInstructionLine("1. Open this page:", "https://console.mistral.ai/api-keys/"),
        ProviderInstructionLine("2. Create a new API key."),
        ProviderInstructionLine("3. Paste the key into API Key.")
    )
    ApiProvider.REVAI -> listOf(
        ProviderInstructionLine("1. Open this page:", "https://www.rev.ai/auth/signup"),
        ProviderInstructionLine("2. Create account and generate access token."),
        ProviderInstructionLine("3. Paste token into API Key.")
    )
    ApiProvider.GROQ -> listOf(
        ProviderInstructionLine("1. Open this page:", "https://console.groq.com/keys"),
        ProviderInstructionLine("2. Create API key."),
        ProviderInstructionLine("3. Paste into API Key.")
    )
    ApiProvider.OPENROUTER -> listOf(
        ProviderInstructionLine("1. Open this page:", "https://openrouter.ai/keys"),
        ProviderInstructionLine("2. Create key."),
        ProviderInstructionLine("3. Paste into API Key.")
    )
    ApiProvider.GEMINI -> listOf(
        ProviderInstructionLine("1. Open this page:", "https://aistudio.google.com/app/apikey"),
        ProviderInstructionLine("2. Create API key."),
        ProviderInstructionLine("3. Paste into API Key.")
    )
    ApiProvider.ANTIGRAVITY -> listOf(
        ProviderInstructionLine("1. Use Google OAuth-backed access to reuse available quota."),
        ProviderInstructionLine("2. Configure Base URL to the Antigravity endpoint."),
        ProviderInstructionLine("3. Keep API key disabled unless your gateway requires one.")
    )
    ApiProvider.HUGGINGFACE -> listOf(
        ProviderInstructionLine("1. Open this page:", "https://huggingface.co/settings/tokens"),
        ProviderInstructionLine("2. Create access token."),
        ProviderInstructionLine("3. Paste token into API Key.")
    )
    ApiProvider.SELFHOSTED_WHISPER -> listOf(
        ProviderInstructionLine("1. Start a local or remote whisper.cpp server."),
        ProviderInstructionLine("2. Put server URL into Base URL (for local default use http://127.0.0.1:8080/)."),
        ProviderInstructionLine("3. If auth is enabled on your gateway, paste token into API Key.")
    )
    ApiProvider.LOCAL_WHISPER -> listOf(
        ProviderInstructionLine("1. Local processing does not require an API key."),
        ProviderInstructionLine("2. Download a whisper.cpp model (.bin) and place it on your device."),
        ProviderInstructionLine("3. Go to 'Local Models' section to scan and select the model file.")
    )
}
