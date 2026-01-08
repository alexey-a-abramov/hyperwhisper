package com.hyperwhisper.ui.about

import android.content.pm.PackageInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.lifecycleScope
import com.hyperwhisper.BuildConfig
import com.hyperwhisper.data.AppearanceSettings
import com.hyperwhisper.data.SettingsRepository
import com.hyperwhisper.ime.update.UpdateManager
import com.hyperwhisper.ime.update.UpdateProbeDetails
import com.hyperwhisper.ui.theme.HyperWhisperTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class AboutActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var updateManager: UpdateManager

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

            // Update probe details state
            var updateProbeDetails by remember { mutableStateOf<UpdateProbeDetails?>(null) }

            // Load probe details on first composition
            androidx.compose.runtime.LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    updateProbeDetails = updateManager.getUpdateProbeDetails()
                }
            }

            HyperWhisperTheme(appearanceSettings = appearanceSettings) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AboutScreen(
                        versionName = versionName,
                        versionCode = versionCode,
                        buildDate = BuildConfig.BUILD_DATE,
                        usageStatistics = usageStatistics,
                        updateProbeDetails = updateProbeDetails,
                        onClearStatistics = {
                            lifecycleScope.launch {
                                settingsRepository.clearStatistics()
                            }
                        },
                        onRefreshUpdateProbe = {
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) {
                                    updateProbeDetails = updateManager.getUpdateProbeDetails()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
