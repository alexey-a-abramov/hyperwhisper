package com.hyperwhisper.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade repository that provides unified access to all settings subsystems
 * Delegates to specialized repositories for actual storage operations
 *
 * This design:
 * - Maintains backward compatibility with existing code
 * - Provides single point of access for all settings
 * - Allows specialized repositories to be used directly when preferred
 * - Eliminates code duplication through delegation
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val apiSettingsRepository: ApiSettingsRepository,
    private val voiceModesRepository: VoiceModesRepository,
    private val appearanceRepository: AppearanceRepository,
    private val usageStatisticsRepository: UsageStatisticsRepository,
    private val historyRepository: HistoryRepository,
    private val languageTrackingRepository: LanguageTrackingRepository,
    private val providerModelTrackingRepository: ProviderModelTrackingRepository,
    private val apiCallLogRepository: ApiCallLogRepository,
    val localModelRepository: LocalModelRepository
) {
    // ============================================================================
    // API Settings - Delegated to ApiSettingsRepository
    // ============================================================================

    val apiSettings: Flow<ApiSettings> = apiSettingsRepository.apiSettings

    suspend fun saveApiSettings(settings: ApiSettings) =
        apiSettingsRepository.saveApiSettings(settings)

    suspend fun updateProviderApiKey(provider: ApiProvider, apiKey: String) =
        apiSettingsRepository.updateProviderApiKey(provider, apiKey)

    suspend fun updateProviderConfig(provider: ApiProvider, customBaseUrl: String, requiresAuth: Boolean) =
        apiSettingsRepository.updateProviderConfig(provider, customBaseUrl, requiresAuth)

    suspend fun updateLlmConfig(llmConfig: LlmConfig) =
        apiSettingsRepository.updateLlmConfig(llmConfig)

    suspend fun updateLlmProviderApiKey(provider: LlmProvider, apiKey: String) =
        apiSettingsRepository.updateLlmProviderApiKey(provider, apiKey)

    suspend fun updateLlmProviderConfig(
        provider: LlmProvider,
        customBaseUrl: String,
        requiresAuth: Boolean?,
    ) = apiSettingsRepository.updateLlmProviderConfig(provider, customBaseUrl, requiresAuth)

    suspend fun recordProviderTested(provider: ApiProvider, timestampMillis: Long) =
        apiSettingsRepository.recordProviderTested(provider, timestampMillis)

    suspend fun recordLlmProviderTested(provider: LlmProvider, timestampMillis: Long) =
        apiSettingsRepository.recordLlmProviderTested(provider, timestampMillis)

    suspend fun updateLocalModelSettings(settings: LocalModelSettings) =
        apiSettingsRepository.updateLocalModelSettings(settings)

    suspend fun resetApiSettingsToDefaults(provider: ApiProvider) =
        apiSettingsRepository.resetToDefaults(provider)

    // ============================================================================
    // Voice Modes - Delegated to VoiceModesRepository
    // ============================================================================

    val voiceModes: Flow<List<VoiceMode>> = voiceModesRepository.voiceModes
    val selectedMode: Flow<String> = voiceModesRepository.selectedMode

    suspend fun saveVoiceModes(modes: List<VoiceMode>) =
        voiceModesRepository.saveVoiceModes(modes)

    suspend fun addVoiceMode(mode: VoiceMode) =
        voiceModesRepository.addVoiceMode(mode)

    suspend fun deleteVoiceMode(modeId: String) =
        voiceModesRepository.deleteVoiceMode(modeId)

    suspend fun updateVoiceMode(mode: VoiceMode) =
        voiceModesRepository.updateVoiceMode(mode)

    suspend fun setSelectedMode(modeId: String) =
        voiceModesRepository.setSelectedMode(modeId)

    // ============================================================================
    // Appearance Settings - Delegated to AppearanceRepository
    // ============================================================================

    val appearanceSettings: Flow<AppearanceSettings> = appearanceRepository.appearanceSettings

    suspend fun saveAppearanceSettings(settings: AppearanceSettings) =
        appearanceRepository.saveAppearanceSettings(settings)

    // ============================================================================
    // Usage Statistics - Delegated to UsageStatisticsRepository
    // ============================================================================

    val usageStatistics: Flow<UsageStatistics> = usageStatisticsRepository.usageStatistics

    suspend fun recordUsage(
        modelId: String,
        inputTokens: Int,
        outputTokens: Int,
        totalTokens: Int,
        audioDurationSeconds: Double,
        outputCharacters: Long = 0L,
        outputBytes: Long = 0L
    ) = usageStatisticsRepository.recordUsage(
        modelId, inputTokens, outputTokens, totalTokens, audioDurationSeconds,
        outputCharacters, outputBytes
    )

    suspend fun clearStatistics() =
        usageStatisticsRepository.clearStatistics()

    // ============================================================================
    // Transcription History - Delegated to HistoryRepository
    // ============================================================================

    val transcriptionHistory: Flow<List<TranscriptionHistoryItem>> =
        historyRepository.transcriptionHistory

    suspend fun addToHistory(text: String, audioFilePath: String? = null) =
        historyRepository.addToHistory(text, audioFilePath)

    suspend fun updateHistoryItem(itemId: String, newText: String) =
        historyRepository.updateHistoryItem(itemId, newText)

    suspend fun clearHistory() =
        historyRepository.clearHistory()

    suspend fun checkHistorySizeReduction(newMaxSize: Int, newUnlimited: Boolean): Int =
        historyRepository.checkHistorySizeReduction(newMaxSize, newUnlimited)

    suspend fun trimHistoryToSize(maxSize: Int) =
        historyRepository.trimHistoryToSize(maxSize)

    // ============================================================================
    // Language Tracking - Delegated to LanguageTrackingRepository
    // ============================================================================

    val recentlyUsedLanguages: Flow<List<String>> =
        languageTrackingRepository.recentlyUsedLanguages

    suspend fun trackLanguageUsage(languageCode: String) =
        languageTrackingRepository.trackLanguageUsage(languageCode)

    // ============================================================================
    // Provider/Model Tracking - Delegated to ProviderModelTrackingRepository
    // ============================================================================

    val recentlyUsedProviderModels: Flow<List<ProviderModelSelection>> =
        providerModelTrackingRepository.recentlyUsedProviderModels

    suspend fun trackProviderModelUsage(provider: ApiProvider, modelId: String) =
        providerModelTrackingRepository.trackProviderModelUsage(provider, modelId)

    // ============================================================================
    // API Call Logs - Delegated to ApiCallLogRepository
    // ============================================================================

    val apiCallLogs: Flow<List<ApiCallLog>> = apiCallLogRepository.logs

    suspend fun addApiCallLog(log: ApiCallLog) =
        apiCallLogRepository.addLog(log)

    suspend fun getApiCallStatistics(): ApiCallStatistics =
        apiCallLogRepository.getStatistics()

    suspend fun clearApiCallLogs() =
        apiCallLogRepository.clearAllLogs()
}
