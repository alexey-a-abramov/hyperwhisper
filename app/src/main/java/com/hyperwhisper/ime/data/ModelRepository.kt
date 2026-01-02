package com.hyperwhisper.data

import android.content.Context
import android.util.Log
import com.hyperwhisper.di.ModelDownloadClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing local whisper.cpp models
 * Handles downloading, storing, and tracking model states
 */
@Singleton
class ModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @ModelDownloadClient private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "ModelRepository"
        private const val MODELS_DIR = "whisper_models"
    }

    private val modelsDir = File(context.filesDir, MODELS_DIR)

    private val _modelStates = MutableStateFlow<Map<WhisperModel, ModelDownloadState>>(emptyMap())
    val modelStates: StateFlow<Map<WhisperModel, ModelDownloadState>> = _modelStates.asStateFlow()

    init {
        // Ensure models directory exists
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
            Log.d(TAG, "Created models directory: ${modelsDir.absolutePath}")
        }

        // Initialize states
        updateModelStates()
    }

    /**
     * Update model download states by checking which files exist
     */
    private fun updateModelStates() {
        val states = WhisperModel.values().associateWith { model ->
            val modelFile = getModelFile(model)
            if (modelFile.exists() && modelFile.length() == model.fileSize) {
                ModelDownloadState.Downloaded
            } else {
                ModelDownloadState.NotDownloaded
            }
        }
        _modelStates.value = states
        Log.d(TAG, "Model states updated: ${states.map { "${it.key.modelName}=${it.value}" }}")
    }

    /**
     * Get model file path
     */
    fun getModelFile(model: WhisperModel): File {
        return File(modelsDir, model.fileName)
    }

    /**
     * Check if model is downloaded and valid
     */
    fun isModelDownloaded(model: WhisperModel): Boolean {
        val file = getModelFile(model)
        val isValid = file.exists() && file.length() == model.fileSize
        Log.d(TAG, "Model ${model.modelName} downloaded: $isValid (exists=${file.exists()}, size=${file.length()}/${model.fileSize})")
        return isValid
    }

    /**
     * Download model from URL with progress tracking
     */
    suspend fun downloadModel(model: WhisperModel): Result<File> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Downloading model: ${model.displayName} from ${model.downloadUrl}")

            // Update state to downloading
            updateModelState(model, ModelDownloadState.Downloading(0f))

            val request = Request.Builder()
                .url(model.downloadUrl)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val error = "Download failed: HTTP ${response.code}"
                Log.e(TAG, error)
                updateModelState(model, ModelDownloadState.Error(error))
                return@withContext Result.failure(Exception(error))
            }

            val body = response.body
            if (body == null) {
                val error = "Empty response body"
                Log.e(TAG, error)
                updateModelState(model, ModelDownloadState.Error(error))
                return@withContext Result.failure(Exception(error))
            }

            val totalBytes = body.contentLength()
            Log.d(TAG, "Download started: ${totalBytes / (1024 * 1024)} MB")

            val modelFile = getModelFile(model)
            val tempFile = File(modelsDir, "${model.fileName}.tmp")

            // Download with progress
            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var downloadedBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // Update progress
                        if (totalBytes > 0) {
                            val progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                            updateModelState(model, ModelDownloadState.Downloading(progress))

                            if (downloadedBytes % (1024 * 1024 * 10) == 0L) { // Log every 10MB
                                Log.d(TAG, "Download progress: ${(progress * 100).toInt()}%")
                            }
                        }
                    }
                }
            }

            // Verify file size
            if (tempFile.length() != model.fileSize) {
                tempFile.delete()
                val error = "Downloaded size mismatch: expected ${model.fileSize}, got ${tempFile.length()}"
                Log.e(TAG, error)
                updateModelState(model, ModelDownloadState.Error(error))
                return@withContext Result.failure(Exception(error))
            }

            // Move temp file to final location
            if (modelFile.exists()) {
                modelFile.delete()
            }
            tempFile.renameTo(modelFile)

            updateModelState(model, ModelDownloadState.Downloaded)
            Log.d(TAG, "Model downloaded successfully: ${modelFile.absolutePath}")

            Result.success(modelFile)

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model: ${model.displayName}", e)
            updateModelState(model, ModelDownloadState.Error(e.message ?: "Unknown error"))
            Result.failure(e)
        }
    }

    /**
     * Delete downloaded model
     */
    suspend fun deleteModel(model: WhisperModel): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val modelFile = getModelFile(model)
            if (modelFile.exists()) {
                modelFile.delete()
                Log.d(TAG, "Model deleted: ${model.displayName}")
            }
            updateModelState(model, ModelDownloadState.NotDownloaded)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting model", e)
            Result.failure(e)
        }
    }

    /**
     * Extract bundled tiny model from assets on first launch
     */
    suspend fun extractBundledModel(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val tinyModel = WhisperModel.TINY
            val modelFile = getModelFile(tinyModel)

            // Skip if already extracted
            if (modelFile.exists() && modelFile.length() == tinyModel.fileSize) {
                Log.d(TAG, "Bundled model already extracted")
                return@withContext Result.success(Unit)
            }

            Log.d(TAG, "Extracting bundled tiny model from assets...")

            try {
                context.assets.open("models/${tinyModel.fileName}").use { input ->
                    FileOutputStream(modelFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytes = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                        }

                        Log.d(TAG, "Extracted ${totalBytes / (1024 * 1024)} MB")
                    }
                }

                updateModelState(tinyModel, ModelDownloadState.Downloaded)
                Log.d(TAG, "Bundled model extracted successfully")
                Result.success(Unit)
            } catch (e: java.io.FileNotFoundException) {
                Log.w(TAG, "Bundled model not found in assets, skipping extraction")
                Result.success(Unit) // Not an error if model isn't bundled
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting bundled model", e)
            Result.failure(e)
        }
    }

    /**
     * Get total storage used by all models
     */
    fun getTotalStorageUsed(): Long {
        return modelsDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Update individual model state
     */
    private fun updateModelState(model: WhisperModel, state: ModelDownloadState) {
        _modelStates.value = _modelStates.value.toMutableMap().apply {
            put(model, state)
        }
    }
}
