package com.hyperwhisper.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.security.ActivitySecretsRevealController
import com.hyperwhisper.security.BiometricGate
import com.hyperwhisper.security.LocalSecretsReveal
import com.hyperwhisper.ui.theme.HyperWhisperTheme
import com.hyperwhisper.ime.update.UpdateCheckResult
import com.hyperwhisper.ime.update.UpdateDialog
import com.hyperwhisper.ime.update.UpdateManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : FragmentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    @Inject
    lateinit var updateManager: UpdateManager

    @Inject
    lateinit var biometricGate: BiometricGate

    // Update dialog state — UpdateDialogHost shows whenever this is non-null.
    private var updateInfo by mutableStateOf<com.hyperwhisper.ime.update.UpdateInfo?>(null)

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
        val initialProvider = intent.getStringExtra(EXTRA_PROVIDER_NAME)
            ?.let { runCatching { ApiProvider.valueOf(it) }.getOrNull() }

        // Check and request microphone permission
        checkAndRequestMicrophonePermission()

        // Check for updates
        checkForUpdates()

        setContent {
            val appearanceSettings by viewModel.appearanceSettings.collectAsState()
            val secretsReveal = remember {
                ActivitySecretsRevealController(this, biometricGate)
            }

            CompositionLocalProvider(LocalSecretsReveal provides secretsReveal) {
                HyperWhisperTheme(appearanceSettings = appearanceSettings) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        SettingsScreen(
                            viewModel = viewModel,
                            initialProvider = initialProvider
                        )
                    }
                }

                // Update dialog — flow extracted to UpdateDialogHost so AboutActivity
                // can host the same dialog without duplicating the download/install glue.
                com.hyperwhisper.ime.update.UpdateDialogHost(
                    updateInfo = updateInfo,
                    updateManager = updateManager,
                    onDismiss = { updateInfo = null }
                )
            }
        }
    }

    companion object {
        const val EXTRA_PROVIDER_NAME = "extra_provider_name"
    }

    private fun checkForUpdates() {
        lifecycleScope.launch {
            when (val result = updateManager.checkForUpdates()) {
                is UpdateCheckResult.UpdateAvailable -> {
                    updateInfo = result.updateInfo
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
