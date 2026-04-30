package com.hyperwhisper.data

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for discovering and managing local AI models (Whisper, Gemma, etc.)
 */
@Singleton
class LocalModelRepository @Inject constructor(
    private val context: Context,
    private val apiSettingsRepository: ApiSettingsRepository
) {
    companion object {
        private const val TAG = "LocalModelRepository"
        
        val WHISPER_EXTENSIONS = listOf(".bin")
        val GEMMA_EXTENSIONS = listOf(".bin", ".gguf")
        
        val SEARCH_PATHS = listOf(
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Models",
            "/storage/emulated/0/Whisper",
            "/storage/emulated/0/LLM",
            "/data/data/com.termux/files/home/models"
        )
    }

    /**
     * Scan for local models in known locations
     */
    suspend fun discoverModels(): List<LocalModelInfo> = withContext(Dispatchers.IO) {
        val models = mutableListOf<LocalModelInfo>()
        
        // Log permission status
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val hasManage = Environment.isExternalStorageManager()
            Log.d(TAG, "Has MANAGE_EXTERNAL_STORAGE: $hasManage")
        }

        // Search in app private directory
        searchInDir(context.filesDir, models)
        
        // Search in common public directories
        SEARCH_PATHS.forEach { path ->
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                // For specific model folders, search recursively (1 level)
                if (dir.name.equals("Models", ignoreCase = true) || dir.name.equals("Whisper", ignoreCase = true)) {
                    searchInDir(dir, models, recursive = true)
                } else {
                    searchInDir(dir, models, recursive = false)
                }
            }
        }
        
        Log.d(TAG, "Discovered ${models.size} models")
        models.distinctBy { it.path }
    }

    private fun searchInDir(dir: File, models: MutableList<LocalModelInfo>, recursive: Boolean = false) {
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val fileName = file.name.lowercase()
                    val type = when {
                        // Whisper models are usually smaller and often have "ggml" or "whisper" in name
                        fileName.endsWith(".bin") && 
                        (fileName.contains("whisper") || fileName.contains("ggml") || file.length() < 600_000_000) -> 
                            LocalModelType.WHISPER
                            
                        // Gemma/Llama are usually .gguf or larger .bin
                        fileName.endsWith(".gguf") || 
                        (fileName.endsWith(".bin") && (fileName.contains("gemma") || fileName.contains("llama") || file.length() > 600_000_000)) -> 
                            LocalModelType.GEMMA
                        else -> null
                    }
                    
                    if (type != null) {
                        models.add(LocalModelInfo(
                            name = file.name,
                            path = file.absolutePath,
                            sizeBytes = file.length(),
                            lastModified = file.lastModified(),
                            type = type
                        ))
                    }
                } else if (recursive && file.isDirectory && !file.name.startsWith(".")) {
                    searchInDir(file, models, recursive = false) // only one level deeper
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching in ${dir.absolutePath}", e)
        }
    }

    /**
     * Verify model integrity using SHA-256
     */
    suspend fun verifyIntegrity(path: String): String = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) return@withContext ""
            
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            file.inputStream().use { input ->
                var bytesRead = input.read(buffer)
                while (bytesRead != -1) {
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = input.read(buffer)
                }
            }
            
            val hashBytes = digest.digest()
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying integrity of $path", e)
            ""
        }
    }

    /**
     * Persist selected model settings
     */
    suspend fun saveSelectedModel(type: LocalModelType, path: String, verify: Boolean = true) {
        val hash = if (verify) verifyIntegrity(path) else ""
        
        val currentSettings = apiSettingsRepository.apiSettings.first().localModelSettings
        val updatedSettings = when (type) {
            LocalModelType.WHISPER -> currentSettings.copy(
                whisperModelPath = path,
                whisperModelHash = hash,
                useLocalWhisper = path.isNotEmpty()
            )
            LocalModelType.GEMMA -> currentSettings.copy(
                gemmaModelPath = path,
                gemmaModelHash = hash,
                useLocalGemma = path.isNotEmpty()
            )
            LocalModelType.LLAMA -> currentSettings.copy(
                gemmaModelPath = path, // Reuse Gemma slot for now
                gemmaModelHash = hash
            )
        }
        
        apiSettingsRepository.updateLocalModelSettings(updatedSettings)
    }
}
