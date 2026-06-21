package com.hyperwhisper.data.prediction

import android.util.Log
import com.hyperwhisper.data.telemetry.PerformanceRepository
import com.hyperwhisper.data.telemetry.SessionType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the telemetry `sessions` table into progress-prediction coefficients.
 *
 * On-device whisper wall-clock is ~linear in audio length but the slope varies
 * by model (tiny vs base vs large) and there's a roughly-fixed overhead from
 * decode / cold-start model load. So per model we least-squares fit
 * `wallMs ≈ slope·audioMs + intercept` from the proven history and persist it.
 * [recalibrate] is what the "Recalculate" button calls; [estimateMs] is what
 * the progress bar consults before each local transcription.
 *
 * Why this exists: the old byte-heuristic estimator keyed on the *cloud*
 * provider/model and never saw local runs, so the local progress bar always
 * fell back to a far-too-fast default and parked at 100% while whisper was
 * still working.
 */
@Singleton
class LocalPredictionCalibrator @Inject constructor(
    private val perf: PerformanceRepository,
    private val store: PredictionCoefficientsStore,
) {
    companion object {
        private const val TAG = "LocalPredictionCalibrator"
        // Below this, a per-model fit isn't trustworthy — fall back to global.
        private const val MIN_SAMPLES = 5
        // Guard against a degenerate non-positive slope from noisy data.
        private const val MIN_SLOPE = 0.01
        // Match the byte-heuristic's shaping so the two estimate paths feel the
        // same: undershoot slightly (the bar caps at 95% anyway) and stay
        // readable on very short clips.
        private const val BUFFER_MS = 1_500L
        private const val MIN_ESTIMATE_MS = 1_500L
    }

    /** Recompute and persist coefficients from all successful on-device sessions. */
    suspend fun recalibrate(): CalibrationSummary {
        val rows = perf.calibrationRows(SessionType.ON_DEVICE)
        val byModel = rows.groupBy { it.modelId }

        val modelCoeffs = mutableMapOf<String, ModelCoeff>()
        val perModelSamples = mutableMapOf<String, Int>()
        for ((modelId, modelRows) in byModel) {
            perModelSamples[modelId] = modelRows.size
            fit(modelRows.map { it.audioDurationMs.toDouble() to it.totalWallMs.toDouble() })
                ?.let { modelCoeffs[modelId] = it }
        }
        val global = fit(rows.map { it.audioDurationMs.toDouble() to it.totalWallMs.toDouble() })

        val coeffs = PredictionCoefficients(
            models = modelCoeffs,
            global = global,
            computedAtEpochMs = System.currentTimeMillis(),
            totalSessions = rows.size,
        )
        store.save(coeffs)
        Log.d(TAG, "Recalibrated: ${modelCoeffs.size} model(s) from ${rows.size} session(s)")
        return CalibrationSummary(
            modelsCalibrated = modelCoeffs.size,
            totalSessions = rows.size,
            perModelSamples = perModelSamples,
            computedAtEpochMs = coeffs.computedAtEpochMs,
        )
    }

    /**
     * Predicted wall-clock for [audioDurationMs] of audio on [modelId] (the
     * telemetry id, e.g. `"Whisper:ggml-base.en.bin"`). Returns null when no
     * usable coefficient exists yet, so the caller can fall back.
     */
    suspend fun estimateMs(modelId: String, audioDurationMs: Long): Long? {
        if (audioDurationMs <= 0) return null
        val coeffs = store.load()
        val coeff = coeffs.models[modelId] ?: coeffs.global ?: return null
        val raw = (coeff.slopeMsPerMs * audioDurationMs + coeff.interceptMs).toLong()
        return (raw + BUFFER_MS).coerceAtLeast(MIN_ESTIMATE_MS)
    }

    /**
     * Least-squares fit of wall(ms) vs audio(ms). Returns null when too thin.
     * When audio lengths barely vary (can't separate slope from intercept),
     * fall back to the average ratio with a zero intercept. Slope is floored at
     * [MIN_SLOPE]; the intercept is kept as-is (the [MIN_ESTIMATE_MS] floor in
     * [estimateMs] handles any small/negative predictions on tiny clips).
     */
    private fun fit(points: List<Pair<Double, Double>>): ModelCoeff? {
        val n = points.size
        if (n < MIN_SAMPLES) return null
        val meanX = points.sumOf { it.first } / n
        val meanY = points.sumOf { it.second } / n
        if (meanX <= 0.0) return null

        var sxx = 0.0
        var sxy = 0.0
        for ((x, y) in points) {
            val dx = x - meanX
            sxx += dx * dx
            sxy += dx * (y - meanY)
        }

        // sxx ~ 0 → essentially one cluster of durations; a slope is meaningless.
        if (sxx < 1.0) {
            return ModelCoeff((meanY / meanX).coerceAtLeast(MIN_SLOPE), 0.0, n)
        }
        val slope = sxy / sxx
        if (slope <= 0.0) {
            return ModelCoeff((meanY / meanX).coerceAtLeast(MIN_SLOPE), 0.0, n)
        }
        val intercept = meanY - slope * meanX
        return ModelCoeff(slope, intercept, n)
    }
}
