package com.hyperwhisper.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.ui.theme.HyperWhisperTheme
import kotlin.system.exitProcess

class CrashActivity : ComponentActivity() {

    private var crashInfo: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        crashInfo = intent.getStringExtra("crash_info") ?: "No crash information available"

        setContent {
            HyperWhisperTheme {
                CrashScreen(
                    crashInfo = crashInfo,
                    onCopy = { copyToClipboard() },
                    onClose = { finish() }
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

    override fun onBackPressed() {
        super.onBackPressed()
        exitProcess(0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashScreen(
    crashInfo: String,
    onCopy: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "App Crashed",
                        fontWeight = FontWeight.Bold
                    )
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
                        onClick = onCopy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Copy Error Details")
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
                        "Please copy the error details below and report this issue.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Divider()

            // Crash details
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = crashInfo,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFE0E0E0),
                    lineHeight = 18.sp
                )
            }
        }
    }
}
