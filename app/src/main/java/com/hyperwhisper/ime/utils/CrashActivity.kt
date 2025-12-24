package com.hyperwhisper.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.addCallback
import androidx.core.content.pm.PackageInfoCompat
import com.hyperwhisper.ui.theme.HyperWhisperTheme
import kotlin.system.exitProcess

class CrashActivity : ComponentActivity() {

    private var crashInfo: String = ""
    private var traceLogs: String = ""
    private var versionInfo: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        crashInfo = intent.getStringExtra("crash_info") ?: "No crash information available"
        traceLogs = intent.getStringExtra("trace_logs") ?: "No trace logs available"

        // Get version info
        versionInfo = try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            "v${packageInfo.versionName} (Build ${PackageInfoCompat.getLongVersionCode(packageInfo)})"
        } catch (e: Exception) {
            "Version unknown"
        }

        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
        }

        setContent {
            HyperWhisperTheme {
                CrashScreen(
                    crashInfo = crashInfo,
                    traceLogs = traceLogs,
                    versionInfo = versionInfo,
                    onCopy = { copyToClipboard() },
                    onCopyTraces = { copyTracesToClipboard() },
                    onSwitchKeyboard = { openKeyboardSettings() },
                    onClose = {
                        finish()
                        exitProcess(0)
                    }
                )
            }
        }
    }

    private fun copyToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("HyperWhisper Crash Report", crashInfo)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Crash report copied to clipboard", Toast.LENGTH_LONG).show()
    }

    private fun copyTracesToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("HyperWhisper Trace Logs", traceLogs)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Trace logs copied to clipboard", Toast.LENGTH_LONG).show()
    }

    private fun openKeyboardSettings() {
        try {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open keyboard settings", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashScreen(
    crashInfo: String,
    traceLogs: String,
    versionInfo: String,
    onCopy: () -> Unit,
    onCopyTraces: () -> Unit,
    onSwitchKeyboard: () -> Unit,
    onClose: () -> Unit
) {
    var showTraces by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "App Crashed",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            versionInfo,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    titleContentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSwitchKeyboard,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Switch to Another Keyboard")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCopy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Copy Error", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = onCopyTraces,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Copy Traces", fontSize = 13.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = { showTraces = !showTraces },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (showTraces) "Show Crash Details" else "Show Trace Logs")
                    }

                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close App")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Error message banner
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "HyperWhisper has crashed",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "Please switch to another keyboard to continue typing.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "You can copy the error details below and report this issue.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Divider()

            // Content area - shows either crash details or trace logs
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Header showing what's being displayed
                Text(
                    text = if (showTraces) "═══ TRACE LOGS ═══" else "═══ CRASH DETAILS ═══",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFB74D),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = if (showTraces) traceLogs else crashInfo,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFE0E0E0),
                    lineHeight = 16.sp
                )
            }
        }
    }
}
