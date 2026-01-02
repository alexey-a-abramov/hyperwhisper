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
            Log.d(TAG, "=== DOWNLOAD START ===")
            Log.d(TAG, "Model: ${model.displayName}")
            Log.d(TAG, "URL: ${model.downloadUrl}")
            Log.d(TAG, "Expected size: ${model.fileSize / (1024 * 1024)}MB")

            // Update state to downloading (0%)
            updateModelState(model, ModelDownloadState.Downloading(0f))
            Log.d(TAG, "State updated to Downloading(0%)")

            val request = Request.Builder()
                .url(model.downloadUrl)
                .build()

            Log.d(TAG, "Executing HTTP request...")
            val response = okHttpClient.newCall(request).execute()
            Log.d(TAG, "HTTP Response: ${response.code} ${response.message}")

            if (!response.isSuccessful) {
                val error = "Download failed: HTTP ${response.code} ${response.message}"
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
            Log.d(TAG, "Content-Length: $totalBytes bytes (${totalBytes / (1024 * 1024)} MB)")
            Log.d(TAG, "Content-Type: ${response.header("Content-Type")}")
            Log.d(TAG, "All headers:")
            response.headers.forEach { header ->
                Log.d(TAG, "  ${header.first}: ${header.second}")
            }

            val modelFile = getModelFile(model)
            val tempFile = File(modelsDir, "${model.fileName}.tmp")

            Log.d(TAG, "=== FILE PATHS ===")
            Log.d(TAG, "Models directory: ${modelsDir.absolutePath}")
            Log.d(TAG, "Target file: ${modelFile.absolutePath}")
            Log.d(TAG, "Temp file: ${tempFile.absolutePath}")
            Log.d(TAG, "Models dir exists: ${modelsDir.exists()}")
            Log.d(TAG, "Models dir writable: ${modelsDir.canWrite()}")

            // Delete temp file if it exists
            if (tempFile.exists()) {
                Log.d(TAG, "Deleting existing temp file (${tempFile.length()} bytes)")
                tempFile.delete()
            }

            // Download with progress
            Log.d(TAG, "=== DOWNLOAD STREAM START ===")
            Log.d(TAG, "Source URL: ${model.downloadUrl}")
            Log.d(TAG, "Destination: ${tempFile.absolutePath}")
            Log.d(TAG, "Expected size: ${model.fileSize} bytes")

            var lastProgressUpdate = 0f
            val progressUpdateThreshold = 0.01f // Update UI every 1% progress
            var downloadedBytes = 0L

            try {
                body.byteStream().use { input ->
                    Log.d(TAG, "Input stream opened: ${input.javaClass.name}")
                    Log.d(TAG, "Input stream available: ${input.available()} bytes")

                    FileOutputStream(tempFile).use { output ->
                        Log.d(TAG, "Output stream opened to: ${tempFile.absolutePath}")

                        val buffer = ByteArray(32 * 1024) // 32KB buffer
                        var bytesRead: Int
                        var readCount = 0

                        Log.d(TAG, "Starting read loop (expected: $totalBytes bytes)")

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            readCount++
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            // Log first few reads for debugging
                            if (readCount <= 5) {
                                Log.d(TAG, "Read #$readCount: $bytesRead bytes (total: $downloadedBytes)")
                            }

                            // Update progress less frequently to avoid UI overhead
                            if (totalBytes > 0) {
                                val progress = downloadedBytes.toFloat() / totalBytes.toFloat()

                                // Update UI only if progress increased by at least 1%
                                if (progress - lastProgressUpdate >= progressUpdateThreshold || progress >= 0.99f) {
                                    updateModelState(model, ModelDownloadState.Downloading(progress))
                                    lastProgressUpdate = progress
                                    Log.d(TAG, "Progress: ${(progress * 100).toInt()}% ($downloadedBytes / $totalBytes bytes, ${downloadedBytes / (1024 * 1024)}MB / ${totalBytes / (1024 * 1024)}MB)")
                                }
                            } else {
                                // If totalBytes is unknown, update every 5MB
                                if (downloadedBytes % (1024 * 1024 * 5) == 0L) {
                                    Log.d(TAG, "Downloaded ${downloadedBytes / (1024 * 1024)}MB (size unknown)")
                                }
                            }
                        }

                        output.flush()
                        Log.d(TAG, "Output stream flushed")
                        Log.d(TAG, "Total reads: $readCount")
                        Log.d(TAG, "Total bytes downloaded: $downloadedBytes")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during stream operation", e)
                throw e
            }

            Log.d(TAG, "=== DOWNLOAD STREAM END ===")
            Log.d(TAG, "Temp file size: ${tempFile.length()} bytes")
            Log.d(TAG, "Temp file exists: ${tempFile.exists()}")

            // Verify file size
            Log.d(TAG, "=== SIZE VERIFICATION ===")
            Log.d(TAG, "Expected: ${model.fileSize} bytes (${model.fileSize / (1024 * 1024)}MB)")
            Log.d(TAG, "Downloaded: ${tempFile.length()} bytes (${tempFile.length() / (1024 * 1024)}MB)")
            Log.d(TAG, "Downloaded variable: $downloadedBytes bytes")
            Log.d(TAG, "Content-Length header: $totalBytes bytes")

            if (tempFile.length() != model.fileSize) {
                val error = buildString {
                    append("Size mismatch!\n")
                    append("Expected: ${model.fileSize / (1024 * 1024)}MB (${model.fileSize} bytes)\n")
                    append("Got: ${tempFile.length() / (1024 * 1024)}MB (${tempFile.length()} bytes)\n")
                    append("Downloaded var: ${downloadedBytes / (1024 * 1024)}MB ($downloadedBytes bytes)\n")
                    append("URL: ${model.downloadUrl}\n")
                    if (tempFile.length() == 0L) {
                        append("ERROR: File is empty! Check network/SSL or redirects.")
                    }
                }
                Log.e(TAG, error)
                Log.e(TAG, "Deleting incomplete temp file: ${tempFile.absolutePath}")
                tempFile.delete()
                updateModelState(model, ModelDownloadState.Error(error))
                return@withContext Result.failure(Exception(error))
            }

            // Move temp file to final location
            Log.d(TAG, "=== FINALIZING ===")
            if (modelFile.exists()) {
                Log.d(TAG, "Deleting existing model file: ${modelFile.absolutePath} (${modelFile.length()} bytes)")
                modelFile.delete()
            }

            Log.d(TAG, "Renaming temp file to final location")
            Log.d(TAG, "  From: ${tempFile.absolutePath}")
            Log.d(TAG, "  To: ${modelFile.absolutePath}")

            val renamed = tempFile.renameTo(modelFile)
            Log.d(TAG, "Rename result: $renamed")

            if (!renamed) {
                val error = "Failed to rename temp file to final location"
                Log.e(TAG, error)
                updateModelState(model, ModelDownloadState.Error(error))
                return@withContext Result.failure(Exception(error))
            }

            updateModelState(model, ModelDownloadState.Downloaded)
            Log.d(TAG, "=== DOWNLOAD COMPLETE ===")
            Log.d(TAG, "Model: ${model.displayName}")
            Log.d(TAG, "File: ${modelFile.absolutePath}")
            Log.d(TAG, "Size: ${modelFile.length()} bytes (${modelFile.length() / (1024 * 1024)}MB)")

            Result.success(modelFile)

        } catch (e: Exception) {
            val errorMsg = buildString {
                append("Download failed: ")
                append(e.javaClass.simpleName)
                append(" - ")
                append(e.message ?: "Unknown error")
            }
            Log.e(TAG, "=== DOWNLOAD ERROR ===", e)
            Log.e(TAG, "Model: ${model.displayName}")
            Log.e(TAG, "Error type: ${e.javaClass.name}")
            Log.e(TAG, "Error message: ${e.message}")
            Log.e(TAG, "Stack trace:", e)

            updateModelState(model, ModelDownloadState.Error(errorMsg))
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
     * Import model from external file (user-selected)
     */
    suspend fun importModelFromFile(model: WhisperModel, sourceUri: android.net.Uri): Result<File> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "=== IMPORTING MODEL FROM FILE ===")
            Log.d(TAG, "Model: ${model.displayName}")
            Log.d(TAG, "Source URI: $sourceUri")
            Log.d(TAG, "Expected size: ${model.fileSize} bytes")

            updateModelState(model, ModelDownloadState.Downloading(0f))

            val modelFile = getModelFile(model)
            val tempFile = File(modelsDir, "${model.fileName}.tmp")

            // Delete temp file if exists
            if (tempFile.exists()) {
                tempFile.delete()
            }

            // Copy from URI to temp file
            var copiedBytes = 0L
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        copiedBytes += bytesRead

                        // Update progress
                        if (model.fileSize > 0) {
                            val progress = copiedBytes.toFloat() / model.fileSize.toFloat()
                            if (progress <= 1.0f) {
                                updateModelState(model, ModelDownloadState.Downloading(progress))
                            }
                        }
                    }
                }
            } ?: run {
                val error = "Failed to open input stream from URI"
                Log.e(TAG, error)
                updateModelState(model, ModelDownloadState.Error(error))
                return@withContext Result.failure(Exception(error))
            }

            Log.d(TAG, "Copied $copiedBytes bytes from file")

            // Verify file size
            if (tempFile.length() != model.fileSize) {
                val error = buildString {
                    append("File size mismatch!\n")
                    append("Expected: ${model.fileSize / (1024 * 1024)}MB\n")
                    append("Got: ${tempFile.length() / (1024 * 1024)}MB\n")
                    append("Please select the correct ${model.modelName} model file.")
                }
                Log.e(TAG, error)
                tempFile.delete()
                updateModelState(model, ModelDownloadState.Error(error))
                return@withContext Result.failure(Exception(error))
            }

            // Move to final location
            if (modelFile.exists()) {
                modelFile.delete()
            }
            tempFile.renameTo(modelFile)

            updateModelState(model, ModelDownloadState.Downloaded)
            Log.d(TAG, "Model imported successfully: ${modelFile.absolutePath}")

            Result.success(modelFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing model", e)
            updateModelState(model, ModelDownloadState.Error(e.message ?: "Import failed"))
            Result.failure(e)
        }
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
