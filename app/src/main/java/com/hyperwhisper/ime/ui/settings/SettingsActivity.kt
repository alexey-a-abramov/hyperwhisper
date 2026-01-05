package com.hyperwhisper.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hyperwhisper.ui.theme.HyperWhisperTheme
import com.hyperwhisper.ime.update.UpdateCheckResult
import com.hyperwhisper.ime.update.UpdateDialog
import com.hyperwhisper.ime.update.UpdateManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    @Inject
    lateinit var updateManager: UpdateManager

    // Update dialog state
    private var updateInfo by mutableStateOf<com.hyperwhisper.ime.update.UpdateInfo?>(null)
    private var showUpdateDialog by mutableStateOf(false)

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Microphone permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "Microphone permission is required for voice input",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check and request microphone permission
        checkAndRequestMicrophonePermission()

        // Check for updates
        checkForUpdates()

        setContent {
            val appearanceSettings by viewModel.appearanceSettings.collectAsState()

            HyperWhisperTheme(appearanceSettings = appearanceSettings) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(
                        viewModel = viewModel,
                        updateManager = updateManager,
                        onShowUpdateDialog = { info ->
                            updateInfo = info
                            showUpdateDialog = true
                        }
                    )
                }
            }

            // Update dialog
            updateInfo?.let { info ->
                val currentVersion = updateManager.getCurrentVersion().second
                UpdateDialog(
                    updateInfo = info,
                    currentVersion = currentVersion,
                    onDismiss = { showUpdateDialog = false },
                    onSkip = {
                        updateManager.skipVersion(info.versionCode)
                        showUpdateDialog = false
                    },
                    onUpdate = { progressCallback, onComplete, onError ->
                        lifecycleScope.launch {
                            when (val result = updateManager.downloadApk(info.apkUrl, progressCallback)) {
                                is com.hyperwhisper.ime.update.DownloadResult.Success -> {
                                    onComplete()
                                    updateManager.installApk()
                                }
                                is com.hyperwhisper.ime.update.DownloadResult.Error -> {
                                    onError(result.message)
                                    Toast.makeText(
                                        this@SettingsActivity,
                                        "Download failed: ${result.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                else -> {}
                            }
                        }
                    }
                )
            }
        }
    }

    private fun checkForUpdates() {
        lifecycleScope.launch {
            when (val result = updateManager.checkForUpdates()) {
                is UpdateCheckResult.UpdateAvailable -> {
                    updateInfo = result.updateInfo
                    showUpdateDialog = true
                }
                else -> {}
            }
        }
    }

    private fun checkAndRequestMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                // Show rationale and request
                Toast.makeText(
                    this,
                    "Microphone permission is needed for voice input",
                    Toast.LENGTH_LONG
                ).show()
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                // Request permission
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}
