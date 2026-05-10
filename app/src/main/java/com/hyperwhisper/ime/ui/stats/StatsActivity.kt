package com.hyperwhisper.ui.stats

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.hyperwhisper.data.AppearanceRepository
import com.hyperwhisper.data.AppearanceSettings
import com.hyperwhisper.ui.theme.HyperWhisperTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class StatsActivity : ComponentActivity() {

    private val viewModel: StatsViewModel by viewModels()

    @Inject lateinit var appearanceRepository: AppearanceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appearance by appearanceRepository.appearanceSettings
                .collectAsState(initial = AppearanceSettings())

            HyperWhisperTheme(appearanceSettings = appearance) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StatsScreen(
                        viewModel = viewModel,
                        onBack = { finish() },
                        onExportClick = {
                            viewModel.exportJsonl { file ->
                                val msg = if (file != null)
                                    "Exported to: ${file.absolutePath}"
                                else
                                    "Export failed (see logs)"
                                Toast.makeText(this@StatsActivity, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }
        }
    }
}
