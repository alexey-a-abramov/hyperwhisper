package com.hyperwhisper.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyperwhisper.data.ModelDownloadState
import com.hyperwhisper.data.ModelRepository
import com.hyperwhisper.data.WhisperModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing local whisper models
 * Handles downloading, deleting, and tracking model states
 */
@HiltViewModel
class ModelManagementViewModel @Inject constructor(
    private val modelRepository: ModelRepository
) : ViewModel() {

    /**
     * StateFlow of model download states
     * Maps each WhisperModel to its current download state
     */
    val modelStates: StateFlow<Map<WhisperModel, ModelDownloadState>> = modelRepository.modelStates

    init {
        // Auto-extract bundled model on first launch
        extractBundledModel()
    }

    /**
     * Download a model from HuggingFace
     */
    fun downloadModel(model: WhisperModel) {
        viewModelScope.launch {
            modelRepository.downloadModel(model)
        }
    }

    /**
     * Delete a downloaded model
     */
    fun deleteModel(model: WhisperModel) {
        viewModelScope.launch {
            modelRepository.deleteModel(model)
        }
    }

    /**
     * Get total storage used by all models
     */
    fun getTotalStorageUsed(): Long {
        return modelRepository.getTotalStorageUsed()
    }

    /**
     * Extract bundled tiny model from assets (call on first launch)
     */
    fun extractBundledModel() {
        viewModelScope.launch {
            modelRepository.extractBundledModel()
        }
    }

    /**
     * Import model from user-selected file
     */
    fun importModelFromFile(model: WhisperModel, uri: android.net.Uri) {
        viewModelScope.launch {
            modelRepository.importModelFromFile(model, uri)
        }
    }
}
