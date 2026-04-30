package com.hyperwhisper.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.settings.components.selectors.CloudProviderSelector
import com.hyperwhisper.ui.settings.dialogs.ProviderWizardDialog
import kotlinx.coroutines.delay

@Composable
fun ApiConfigSection(
    provider: ApiProvider,
    baseUrl: String,
    apiKey: String,
    requiresAuth: Boolean,
    modelId: String,
    inputLanguage: String,
    outputLanguage: String,
    connectionTestState: com.hyperwhisper.ui.settings.ConnectionTestState,
    llmApiKey: String = "", 
    onProviderChange: (ApiProvider) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onRequiresAuthChange: (Boolean) -> Unit,
    onModelIdChange: (String) -> Unit,
    onInputLanguageChange: (String) -> Unit,
    onOutputLanguageChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onResetDefaults: () -> Unit,
    onShowModelInfo: () -> Unit,
    onShowProviderKeyHelp: () -> Unit,
    onReuseProviderKeyForLlm: () -> Unit,
    onShowInputLanguageInfo: () -> Unit,
    onShowLogsDialog: () -> Unit,
    onShowApiCallLogs: () -> Unit,
    onResetConnectionTestState: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var showWizard by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Active Provider Selection
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Active Transcription Provider", fontWeight = FontWeight.Bold)
                
                CloudProviderSelector(
                    selectedProvider = provider,
                    onProviderSelected = onProviderChange
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Current Model: $modelId", fontSize = 12.sp)
                        Text("Endpoint: ${baseUrl.take(30)}...", fontSize = 12.sp)
                    }
                    
                    Button(
                        onClick = { showWizard = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Edit Config")
                    }
                }
            }
        }

        // Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onTestConnection,
                modifier = Modifier.weight(1f),
                enabled = (!requiresAuth || apiKey.isNotBlank()) && baseUrl.isNotBlank()
            ) {
                Text(strings.testConnection)
            }

            OutlinedButton(
                onClick = onShowApiCallLogs,
                modifier = Modifier.weight(1f)
            ) {
                Text("API Logs")
            }
        }

        // Connection test result
        ConnectionTestResult(connectionTestState, strings, onResetConnectionTestState)

        if (showWizard) {
            ProviderWizardDialog(
                initialProvider = provider,
                initialBaseUrl = baseUrl,
                initialApiKey = apiKey,
                initialRequiresAuth = requiresAuth,
                initialModelId = modelId,
                initialInputLanguage = inputLanguage,
                initialOutputLanguage = outputLanguage,
                onDismiss = { showWizard = false },
                onSave = { p, b, k, r, m, i, o ->
                    onProviderChange(p)
                    onBaseUrlChange(b)
                    onApiKeyChange(k)
                    onRequiresAuthChange(r)
                    onModelIdChange(m)
                    onInputLanguageChange(i)
                    onOutputLanguageChange(o)
                    showWizard = false
                }
            )
        }
    }
}

@Composable
private fun ConnectionTestResult(
    state: com.hyperwhisper.ui.settings.ConnectionTestState,
    strings: com.hyperwhisper.localization.Strings,
    onReset: () -> Unit
) {
    when (state) {
        is com.hyperwhisper.ui.settings.ConnectionTestState.Testing -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(strings.testingConnection)
                }
            }
        }
        is com.hyperwhisper.ui.settings.ConnectionTestState.Success -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Text(
                    text = state.message,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            LaunchedEffect(Unit) {
                delay(3000)
                onReset()
            }
        }
        is com.hyperwhisper.ui.settings.ConnectionTestState.Error -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = state.message,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            LaunchedEffect(Unit) {
                delay(5000)
                onReset()
            }
        }
        else -> {}
    }
}
