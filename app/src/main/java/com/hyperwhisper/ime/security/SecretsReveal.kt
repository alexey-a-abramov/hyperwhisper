package com.hyperwhisper.security

import androidx.compose.runtime.compositionLocalOf
import androidx.fragment.app.FragmentActivity

/**
 * Compose-side handle for triggering the biometric/device-credential prompt
 * before a secret is revealed or exported. Supplied at the activity's content
 * root via [LocalSecretsReveal] so any nested composable can request a gate
 * check without knowing about the host activity directly.
 */
interface SecretsRevealController {
    fun request(
        title: String,
        subtitle: String,
        onGranted: () -> Unit,
        onDenied: (Denial) -> Unit = {},
    )

    enum class Denial { CANCELLED, NOT_ENROLLED, UNAVAILABLE, FAILED }
}

val LocalSecretsReveal = compositionLocalOf<SecretsRevealController> {
    error("LocalSecretsReveal not provided. Wrap your screen in CompositionLocalProvider.")
}

/**
 * Default activity-bound controller. Holds a weak conceptual link to the
 * activity (passed each call site for safety) — but in practice we keep one
 * instance per [FragmentActivity] lifetime in the Activity's setContent block.
 */
class ActivitySecretsRevealController(
    private val activity: FragmentActivity,
    private val gate: BiometricGate,
    private val titleFallback: String = "Authenticate",
    private val subtitleFallback: String = "Confirm device credential",
    private val notEnrolledMessage: String = "Set up a device lock to reveal secrets.",
) : SecretsRevealController {

    override fun request(
        title: String,
        subtitle: String,
        onGranted: () -> Unit,
        onDenied: (SecretsRevealController.Denial) -> Unit,
    ) {
        when (gate.availability()) {
            BiometricGate.Availability.READY -> {
                gate.request(
                    activity = activity,
                    title = title.ifEmpty { titleFallback },
                    subtitle = subtitle.ifEmpty { subtitleFallback },
                ) { result ->
                    when (result) {
                        BiometricGate.Result.Success -> onGranted()
                        BiometricGate.Result.Cancelled ->
                            onDenied(SecretsRevealController.Denial.CANCELLED)
                        is BiometricGate.Result.Failed ->
                            onDenied(SecretsRevealController.Denial.FAILED)
                    }
                }
            }
            BiometricGate.Availability.NOT_ENROLLED ->
                onDenied(SecretsRevealController.Denial.NOT_ENROLLED)
            BiometricGate.Availability.UNAVAILABLE ->
                onDenied(SecretsRevealController.Denial.UNAVAILABLE)
        }
    }
}
