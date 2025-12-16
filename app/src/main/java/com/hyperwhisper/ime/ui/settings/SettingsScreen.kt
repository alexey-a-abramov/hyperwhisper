package com.hyperwhisper.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.VoiceMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val apiSettings by viewModel.apiSettings.collectAsState()
    val voiceModes by viewModel.voiceModes.collectAsState()

    var provider by remember { mutableStateOf(apiSettings.provider) }
    var baseUrl by remember { mutableStateOf(apiSettings.baseUrl) }
    var apiKey by remember { mutableStateOf(apiSettings.apiKey) }
    var modelId by remember { mutableStateOf(apiSettings.modelId) }

    var showAddModeDialog by remember { mutableStateOf(false) }

    // Update fields when settings change
    LaunchedEffect(apiSettings) {
        provider = apiSettings.provider
        baseUrl = apiSettings.baseUrl
        apiKey = apiSettings.apiKey
        modelId = apiSettings.modelId
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HyperWhisper Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API Settings Section
            item {
                Text(
                    text = "API Configuration",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                ProviderSelector(
                    selectedProvider = provider,
                    onProviderSelected = { provider = it }
                )
            }

            item {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.openai.com/v1/") },
                    supportingText = { Text("Must end with /") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("Model ID") },
                    placeholder = { Text("whisper-1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Button(
                    onClick = {
                        viewModel.saveApiSettings(provider, baseUrl, apiKey, modelId)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save API Settings")
                }
            }

            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Voice Modes Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Voice Modes",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { showAddModeDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Mode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            items(voiceModes) { mode ->
                ModeCard(
                    mode = mode,
                    onDelete = { viewModel.deleteVoiceMode(mode.id) }
                )
            }
        }
    }

    if (showAddModeDialog) {
        AddModeDialog(
            onDismiss = { showAddModeDialog = false },
            onAdd = { name, prompt ->
                viewModel.addVoiceMode(name, prompt)
                showAddModeDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelector(
    selectedProvider: ApiProvider,
    onProviderSelected: (ApiProvider) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = when (selectedProvider) {
                ApiProvider.OPENAI -> "OpenAI / Groq"
                ApiProvider.OPENROUTER -> "OpenRouter"
            },
            onValueChange = {},
            readOnly = true,
            label = { Text("API Provider") },
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
            DropdownMenuItem(
                text = { Text("OpenAI / Groq") },
                onClick = {
                    onProviderSelected(ApiProvider.OPENAI)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("OpenRouter") },
                onClick = {
                    onProviderSelected(ApiProvider.OPENROUTER)
                    expanded = false
                }
            )
        }
    }
}

@Composable
fun ModeCard(
    mode: VoiceMode,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = mode.systemPrompt,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (mode.isBuiltIn) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Built-in",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (!mode.isBuiltIn) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Mode",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun AddModeDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Voice Mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Mode Name") },
                    placeholder = { Text("e.g., Formal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Prompt") },
                    placeholder = { Text("e.g., Transcribe and make it formal") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && systemPrompt.isNotBlank()) {
                        onAdd(name, systemPrompt)
                    }
                },
                enabled = name.isNotBlank() && systemPrompt.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
