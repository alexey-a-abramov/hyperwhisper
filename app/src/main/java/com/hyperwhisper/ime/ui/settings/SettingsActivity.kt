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
import java.io.File
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
                val isLocalBuild = info.apkUrl.startsWith("/") || info.buildTimestamp > 0

                UpdateDialog(
                    updateInfo = info,
                    currentVersion = currentVersion,
                    onDismiss = { showUpdateDialog = false },
                    onSkip = {
                        // Skip by build timestamp for local builds, version code for remote
                        if (info.buildTimestamp > 0) {
                            updateManager.skipBuildTimestamp(info.buildTimestamp)
                        } else {
                            updateManager.skipVersion(info.versionCode)
                        }
                        showUpdateDialog = false
                    },
                    onUpdate = { progressCallback, onComplete, onError ->
                        lifecycleScope.launch {
                            try {
                                if (isLocalBuild && updateManager.isLocalPath(info.apkUrl)) {
                                    // For local builds, install directly without copying
                                    val sourceFile = File(info.apkUrl)
                                    if (!sourceFile.exists()) {
                                        onError("Local APK file not found: ${info.apkUrl}")
                                        Toast.makeText(
                                            this@SettingsActivity,
                                            "Local APK not found: ${info.apkUrl}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@launch
                                    }

                                    // Quick progress for local install
                                    progressCallback(100)
                                    onComplete()

                                    // Install directly from local path
                                    updateManager.installApk(info.apkUrl)
                                } else {
                                    // For remote updates, download normally
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
                            } catch (e: Exception) {
                                onError(e.message ?: "Unknown error")
                                Toast.makeText(
                                    this@SettingsActivity,
                                    "Update failed: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
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
