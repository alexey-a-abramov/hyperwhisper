package com.hyperwhisper.ui.logs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.hyperwhisper.ui.theme.HyperWhisperTheme
import com.hyperwhisper.utils.TraceLogger
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LogsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HyperWhisperTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LogsScreen(
                        logs = TraceLogger.getTraces(),
                        onClearLogs = {
                            TraceLogger.clear()
                            // Refresh by recreating activity
                            recreate()
                        }
                    )
                }
            }
        }
    }
}
