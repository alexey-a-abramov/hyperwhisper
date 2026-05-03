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
        
        // Note: do not list /data/data/<otherpkg>/... here — Android sandboxes
        // each app's private dir, so HyperWhisper cannot read Termux's home
        // even with MANAGE_EXTERNAL_STORAGE. Models must live under /sdcard.
        val SEARCH_PATHS = listOf(
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Models",
            "/storage/emulated/0/Whisper",
            "/storage/emulated/0/LLM"
        )

        // Top-level dir names whose contents are searched recursively (1 level).
        // Lets users organize like /sdcard/LLM/Whisper/ggml-base.en.bin.
        private val RECURSIVE_DIR_NAMES = setOf("models", "whisper", "llm")
    }

    /**
     * Scan for local models in known locations
     */
    /**
     * True on Android 11+ when the user has granted MANAGE_EXTERNAL_STORAGE.
     * Without this permission, [discoverModels] silently returns nothing
     * because the SEARCH_PATHS under /sdcard are unreadable. UI should query
     * this flag and prompt the user when it's false.
     */
    fun hasFullStorageAccess(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
    }

    suspend fun discoverModels(): List<LocalModelInfo> = withContext(Dispatchers.IO) {
        val models = mutableListOf<LocalModelInfo>()

        // Loud diagnostic: missing MANAGE_EXTERNAL_STORAGE silently returns
        // an empty list, which has burned us. Make it obvious in logs.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val hasManage = Environment.isExternalStorageManager()
            if (hasManage) {
                Log.d(TAG, "MANAGE_EXTERNAL_STORAGE: GRANTED")
            } else {
                Log.w(TAG,
                    "MANAGE_EXTERNAL_STORAGE NOT GRANTED — discovery will only " +
                        "see files under filesDir. Models in /sdcard/LLM/Whisper/, " +
                        "/sdcard/Models/, /sdcard/Download/ are invisible. " +
                        "User must grant via Settings → Apps → HyperWhisper → " +
                        "Permissions → Special access → All files."
                )
            }
        }

        // Search in app private directory
        searchInDir(context.filesDir, models)
        
        // Search in common public directories
        SEARCH_PATHS.forEach { path ->
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                val recursive = dir.name.lowercase() in RECURSIVE_DIR_NAMES
                searchInDir(dir, models, recursive = recursive)
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
