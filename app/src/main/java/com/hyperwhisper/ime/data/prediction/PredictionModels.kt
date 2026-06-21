package com.hyperwhisper.data.prediction

/**
 * A per-model linear fit of transcription wall-clock against audio length:
 * `wallMs ≈ slopeMsPerMs · audioMs + interceptMs`. The intercept absorbs
 * roughly-fixed overhead (model load on cold runs, decode); the slope is the
 * real-time factor. [sampleCount] is how many sessions fed the fit.
 */
data class ModelCoeff(
    val slopeMsPerMs: Double,
    val interceptMs: Double,
    val sampleCount: Int,
)

/**
 * Persisted calibration: one [ModelCoeff] per telemetry `model_id` (e.g.
 * `"Whisper:ggml-base.en.bin"`) plus a [global] fit used as the fallback when a
 * specific model has no usable fit yet.
 */
data class PredictionCoefficients(
    val models: Map<String, ModelCoeff> = emptyMap(),
    val global: ModelCoeff? = null,
    val computedAtEpochMs: Long = 0L,
    val totalSessions: Int = 0,
) {
    companion object {
        val EMPTY = PredictionCoefficients()
    }
}

/** Result of a [LocalPredictionCalibrator.recalibrate] run, for the settings UI. */
data class CalibrationSummary(
    val modelsCalibrated: Int,
    val totalSessions: Int,
    val perModelSamples: Map<String, Int>,
    val computedAtEpochMs: Long,
)
