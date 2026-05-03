package com.hyperwhisper.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * BiometricPrompt-backed [BiometricGate] implementation.
 *
 * Authenticator selection: `BIOMETRIC_STRONG | BIOMETRIC_WEAK | DEVICE_CREDENTIAL`.
 * Combining strong+weak biometrics covers the widest device fleet, and including
 * DEVICE_CREDENTIAL means a user with no enrolled fingerprint/face still has a
 * usable path via PIN/pattern/password.
 */
class AndroidBiometricGate(private val appContext: Context) : BiometricGate {

    private val authenticators =
        BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    override fun availability(): BiometricGate.Availability {
        val mgr = BiometricManager.from(appContext)
        return when (mgr.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricGate.Availability.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                BiometricGate.Availability.NOT_ENROLLED
            else -> BiometricGate.Availability.UNAVAILABLE
        }
    }

    override fun request(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cancelLabel: String?,
        onResult: (BiometricGate.Result) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(BiometricGate.Result.Success)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onResult(
                    if (cancelled) BiometricGate.Result.Cancelled
                    else BiometricGate.Result.Failed(errString.toString()),
                )
            }
            override fun onAuthenticationFailed() {
                // Single attempt failure — let BiometricPrompt keep showing
                // until the user gives up or succeeds.
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val infoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
        // Negative button text is illegal when DEVICE_CREDENTIAL is allowed —
        // the system supplies its own. So we ignore [cancelLabel] in that case.
        prompt.authenticate(infoBuilder.build())
    }
}
