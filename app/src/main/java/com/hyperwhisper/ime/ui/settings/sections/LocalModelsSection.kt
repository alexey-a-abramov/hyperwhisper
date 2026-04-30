package com.hyperwhisper.ui.settings.sections

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.LocalModelInfo
import com.hyperwhisper.data.LocalModelSettings
import com.hyperwhisper.data.LocalModelType
import com.hyperwhisper.ui.settings.components.cards.SectionCard
import kotlinx.coroutines.launch

@Composable
fun LocalModelsSection(
    localSettings: LocalModelSettings,
    discoveredModels: List<LocalModelInfo>,
    onSettingsChange: (LocalModelSettings) -> Unit,
    onDiscoverModels: () -> Unit,
    onVerifyModel: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var hasFullStorageAccess by remember { 
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            SectionCard(
                title = "Storage Permissions",
                icon = Icons.Default.Security
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasFullStorageAccess) Icons.Default.Verified else Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (hasFullStorageAccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (hasFullStorageAccess) "Storage Access Granted" else "Full Storage Access Required",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Needed to find models in Downloads and other shared folders",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    if (!hasFullStorageAccess) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                    intent.addCategory("android.intent.category.DEFAULT")
                                    intent.data = Uri.parse("package:${context.packageName}")
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent()
                                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Grant All Files Access")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                hasFullStorageAccess = Environment.isExternalStorageManager()
                                onDiscoverModels()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Refresh Permission Status")
                        }
                    }
                }
            }
        }

        SectionCard(
            title = "General Settings",
            icon = Icons.Default.Memory
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-discover Models", fontWeight = FontWeight.Medium)
                        Text("Scan common folders for .bin and .gguf files", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = localSettings.autoDiscover,
                        onCheckedChange = { onSettingsChange(localSettings.copy(autoDiscover = it)) }
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Processing Threads", fontWeight = FontWeight.Medium)
                        Text("Higher is faster but uses more battery", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    var threadsText by remember { mutableStateOf(localSettings.threads.toString()) }
                    OutlinedTextField(
                        value = threadsText,
                        onValueChange = { 
                            threadsText = it
                            it.toIntOrNull()?.let { num ->
                                onSettingsChange(localSettings.copy(threads = num.coerceIn(1, 16)))
                            }
                        },
                        modifier = Modifier.width(80.dp),
                        singleLine = true
                    )
                }
                
                Button(
                    onClick = onDiscoverModels,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Rescan for Models")
                }
            }
        }

        ModelTypeSection(
            title = "Whisper Models (STT)",
            models = discoveredModels.filter { it.type == LocalModelType.WHISPER },
            selectedPath = localSettings.whisperModelPath,
            isEnabled = localSettings.useLocalWhisper,
            onEnabledChange = { onSettingsChange(localSettings.copy(useLocalWhisper = it)) },
            onSelect = { onSettingsChange(localSettings.copy(whisperModelPath = it, useLocalWhisper = true)) },
            onVerify = onVerifyModel
        )

        ModelTypeSection(
            title = "Gemma/Llama Models (LLM)",
            models = discoveredModels.filter { it.type == LocalModelType.GEMMA || it.type == LocalModelType.LLAMA },
            selectedPath = localSettings.gemmaModelPath,
            isEnabled = localSettings.useLocalGemma,
            onEnabledChange = { onSettingsChange(localSettings.copy(useLocalGemma = it)) },
            onSelect = { onSettingsChange(localSettings.copy(gemmaModelPath = it, useLocalGemma = true)) },
            onVerify = onVerifyModel
        )
    }
}

@Composable
private fun ModelTypeSection(
    title: String,
    models: List<LocalModelInfo>,
    selectedPath: String,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onVerify: (String) -> Unit
) {
    SectionCard(
        title = title,
        icon = Icons.Default.Description
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enable Local Processing", fontWeight = FontWeight.Medium)
                Switch(checked = isEnabled, onCheckedChange = onEnabledChange)
            }
            
            if (models.isEmpty()) {
                Text(
                    "No models found. Place .bin files in Downloads/Models folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text("Discovered Models:", style = MaterialTheme.typography.labelLarge)
                models.forEach { model ->
                    ModelItem(
                        model = model,
                        isSelected = model.path == selectedPath,
                        onSelect = { onSelect(model.path) },
                        onVerify = { onVerify(model.path) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelItem(
    model: LocalModelInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onVerify: () -> Unit
) {
    Surface(
        onClick = onSelect,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = isSelected, onClick = onSelect)
            Column(modifier = Modifier.weight(1f)) {
                Text(model.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${model.sizeBytes / 1024 / 1024} MB | ${model.path.takeLast(30)}...", fontSize = 10.sp)
            }
            IconButton(onClick = onVerify) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = "Verify Integrity",
                    tint = if (model.hash.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
