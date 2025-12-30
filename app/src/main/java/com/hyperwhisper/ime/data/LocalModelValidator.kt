package com.hyperwhisper.data

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validator for local whisper.cpp models
 * Checks prerequisites before allowing LOCAL provider usage
 */
@Singleton
class LocalModelValidator @Inject constructor(
    private val modelRepository: ModelRepository
) {
    companion object {
        private const val TAG = "LocalModelValidator"
    }

    /**
     * Validate that a model is downloaded and ready for use
     * @param model The whisper model to validate
     * @return Result with true if valid, failure with error message if not
     */
    suspend fun validateModel(model: WhisperModel): Result<Boolean> {
        Log.d(TAG, "Validating model: ${model.displayName}")

        // Check if model is downloaded
        val isDownloaded = modelRepository.isModelDownloaded(model)
        if (!isDownloaded) {
            val error = "Model ${model.displayName} is not downloaded"
            Log.w(TAG, error)
            return Result.failure(IllegalStateException(error))
        }

        // Check file size integrity
        val modelFile = modelRepository.getModelFile(model)
        if (modelFile.length() != model.fileSize) {
            val error = "Model file corrupted: expected ${model.fileSize} bytes, got ${modelFile.length()} bytes"
            Log.e(TAG, error)
            return Result.failure(IllegalStateException(error))
        }

        // Check file is readable
        if (!modelFile.canRead()) {
            val error = "Model file is not readable: ${modelFile.absolutePath}"
            Log.e(TAG, error)
            return Result.failure(IllegalStateException(error))
        }

        Log.d(TAG, "Model ${model.displayName} validated successfully")
        return Result.success(true)
    }

    /**
     * Check if LOCAL provider can be used (at least one model is valid)
     * @return Result with true if any model is ready, failure if none are available
     */
    suspend fun validateLocalProviderAvailable(): Result<Boolean> {
        Log.d(TAG, "Checking if LOCAL provider is available")

        val anyModelReady = WhisperModel.values().any { model ->
            validateModel(model).isSuccess
        }

        return if (anyModelReady) {
            Log.d(TAG, "LOCAL provider is available")
            Result.success(true)
        } else {
            val error = "No local models are downloaded. Please download at least one model to use LOCAL provider."
            Log.w(TAG, error)
            Result.failure(IllegalStateException(error))
        }
    }

    /**
     * Get validation status for all models
     * @return Map of model to validation result
     */
    suspend fun validateAllModels(): Map<WhisperModel, Result<Boolean>> {
        return WhisperModel.values().associateWith { model ->
            validateModel(model)
        }
    }
}
