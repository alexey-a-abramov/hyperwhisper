package com.hyperwhisper.ui.about

import android.content.pm.PackageInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.lifecycleScope
import com.hyperwhisper.data.AppearanceSettings
import com.hyperwhisper.data.SettingsRepository
import com.hyperwhisper.ui.theme.HyperWhisperTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AboutActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo).toInt()

        setContent {
            val appearanceSettings by settingsRepository.appearanceSettings.collectAsState(
                initial = AppearanceSettings()
            )
            val usageStatistics by settingsRepository.usageStatistics.collectAsState(
                initial = com.hyperwhisper.data.UsageStatistics()
            )

            HyperWhisperTheme(appearanceSettings = appearanceSettings) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AboutScreen(
                        versionName = versionName,
                        versionCode = versionCode,
                        usageStatistics = usageStatistics,
                        onClearStatistics = {
                            lifecycleScope.launch {
                                settingsRepository.clearStatistics()
                            }
                        }
                    )
                }
            }
        }
    }
}
