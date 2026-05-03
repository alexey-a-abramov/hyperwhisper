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
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.localization.Strings
import com.hyperwhisper.ui.util.localizedDisplayName

@Composable
fun ProviderKeyInstructionsDialog(
    provider: ApiProvider,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val strings = LocalStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.dialogProviderKeyTitle) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${strings.dialogModelInfoProviderPrefix}${provider.localizedDisplayName()}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Divider()
                instructionsFor(provider, strings).forEach { line ->
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
            TextButton(onClick = onDismiss) { Text(strings.close.uppercase()) }
        }
    )
}

private data class ProviderInstructionLine(
    val text: String,
    val url: String? = null
)

private fun instructionsFor(provider: ApiProvider, s: Strings): List<ProviderInstructionLine> = when (provider) {
    ApiProvider.OPENAI -> listOf(
        ProviderInstructionLine(s.providerKeyOpenaiStep1, "https://platform.openai.com/api-keys"),
        ProviderInstructionLine(s.providerKeyOpenaiStep2),
        ProviderInstructionLine(s.providerKeyOpenaiStep3)
    )
    ApiProvider.DEEPGRAM -> listOf(
        ProviderInstructionLine(s.providerKeyDeepgramStep1, "https://console.deepgram.com/project"),
        ProviderInstructionLine(s.providerKeyDeepgramStep2),
        ProviderInstructionLine(s.providerKeyDeepgramStep3)
    )
    ApiProvider.ASSEMBLYAI -> listOf(
        ProviderInstructionLine(s.providerKeyAssemblyStep1, "https://www.assemblyai.com/dashboard"),
        ProviderInstructionLine(s.providerKeyAssemblyStep2),
        ProviderInstructionLine(s.providerKeyAssemblyStep3)
    )
    ApiProvider.GOOGLE_CLOUD -> listOf(
        ProviderInstructionLine(s.providerKeyGoogleCloudStep1, "https://console.cloud.google.com/"),
        ProviderInstructionLine(s.providerKeyGoogleCloudStep2),
        ProviderInstructionLine(s.providerKeyGoogleCloudStep3)
    )
    ApiProvider.AWS_TRANSCRIBE -> listOf(
        ProviderInstructionLine(s.providerKeyAwsStep1, "https://console.aws.amazon.com/"),
        ProviderInstructionLine(s.providerKeyAwsStep2),
        ProviderInstructionLine(s.providerKeyAwsStep3)
    )
    ApiProvider.AZURE_SPEECH -> listOf(
        ProviderInstructionLine(s.providerKeyAzureStep1, "https://portal.azure.com/"),
        ProviderInstructionLine(s.providerKeyAzureStep2),
        ProviderInstructionLine(s.providerKeyAzureStep3)
    )
    ApiProvider.DEEPSEEK -> listOf(
        ProviderInstructionLine(s.providerKeyDeepseekStep1, "https://platform.deepseek.com/api_keys"),
        ProviderInstructionLine(s.providerKeyDeepseekStep2),
        ProviderInstructionLine(s.providerKeyDeepseekStep3)
    )
    ApiProvider.MISTRAL -> listOf(
        ProviderInstructionLine(s.providerKeyMistralStep1, "https://console.mistral.ai/api-keys/"),
        ProviderInstructionLine(s.providerKeyMistralStep2),
        ProviderInstructionLine(s.providerKeyMistralStep3)
    )
    ApiProvider.REVAI -> listOf(
        ProviderInstructionLine(s.providerKeyRevStep1, "https://www.rev.ai/auth/signup"),
        ProviderInstructionLine(s.providerKeyRevStep2),
        ProviderInstructionLine(s.providerKeyRevStep3)
    )
    ApiProvider.GROQ -> listOf(
        ProviderInstructionLine(s.providerKeyGroqStep1, "https://console.groq.com/keys"),
        ProviderInstructionLine(s.providerKeyGroqStep2),
        ProviderInstructionLine(s.providerKeyGroqStep3)
    )
    ApiProvider.OPENROUTER -> listOf(
        ProviderInstructionLine(s.providerKeyOpenrouterStep1, "https://openrouter.ai/keys"),
        ProviderInstructionLine(s.providerKeyOpenrouterStep2),
        ProviderInstructionLine(s.providerKeyOpenrouterStep3)
    )
    ApiProvider.GEMINI -> listOf(
        ProviderInstructionLine(s.providerKeyGeminiStep1, "https://aistudio.google.com/app/apikey"),
        ProviderInstructionLine(s.providerKeyGeminiStep2),
        ProviderInstructionLine(s.providerKeyGeminiStep3)
    )
    ApiProvider.ANTIGRAVITY -> listOf(
        ProviderInstructionLine(s.providerKeyAntigravityStep1),
        ProviderInstructionLine(s.providerKeyAntigravityStep2),
        ProviderInstructionLine(s.providerKeyAntigravityStep3)
    )
    ApiProvider.HUGGINGFACE -> listOf(
        ProviderInstructionLine(s.providerKeyHuggingfaceStep1, "https://huggingface.co/settings/tokens"),
        ProviderInstructionLine(s.providerKeyHuggingfaceStep2),
        ProviderInstructionLine(s.providerKeyHuggingfaceStep3)
    )
    ApiProvider.SELFHOSTED_WHISPER -> listOf(
        ProviderInstructionLine(s.providerKeySelfhostedStep1),
        ProviderInstructionLine(s.providerKeySelfhostedStep2),
        ProviderInstructionLine(s.providerKeySelfhostedStep3)
    )
    ApiProvider.LOCAL_WHISPER -> listOf(
        ProviderInstructionLine(s.providerKeyLocalStep1),
        ProviderInstructionLine(s.providerKeyLocalStep2),
        ProviderInstructionLine(s.providerKeyLocalStep3)
    )
}
