package com.hyperwhisper.data.telemetry

import android.os.SystemClock
import java.util.UUID

class SessionTimer private constructor(
    val sessionId: String,
    val startedAtWallMs: Long,
    private val startedAtElapsedMs: Long,
    val sessionType: SessionType,
    val provider: String,
    val modelId: String,
    val coldStartKind: ColdStartKind,
    val device: DeviceSnapshot,
    val inputLanguage: String?
) {
    private data class Boundary(
        val name: String,
        val ordinal: Int,
        val startElapsed: Long,
        var endElapsed: Long? = null
    )

    private val phases = mutableListOf<Boundary>()
    private var open: Boundary? = null
    private var nextOrdinal = 0
    private val lock = Any()

    /** Begin a phase. Auto-closes any currently-open phase. */
    fun mark(phaseName: String) {
        synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            open?.let {
                it.endElapsed = now
                phases.add(it)
            }
            open = Boundary(phaseName, nextOrdinal++, now)
        }
    }

    /** Close the currently-open phase without starting a new one. */
    fun endCurrentPhase() {
        synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            open?.let {
                it.endElapsed = now
                phases.add(it)
            }
            open = null
        }
    }

    suspend fun commit(
        repo: PerformanceRepository,
        audioDurationMs: Long,
        outputChars: Int,
        inputTokens: Int?,
        outputTokens: Int?,
        totalTokens: Int?,
        detectedLanguage: String?,
        success: Boolean,
        errorKind: String?,
        retryOf: String? = null
    ) {
        endCurrentPhase()
        val totalWallMs = SystemClock.elapsedRealtime() - startedAtElapsedMs
        val phasesCopy = synchronized(lock) { phases.toList() }
        val phaseRows = phasesCopy.map { b ->
            SessionPhaseEntity(
                sessionId = sessionId,
                phaseName = b.name,
                ordinal = b.ordinal,
                durationMs = (b.endElapsed ?: b.startElapsed) - b.startElapsed
            )
        }
        val session = SessionEntity(
            id = sessionId,
            startedAt = startedAtWallMs,
            sessionType = sessionType,
            provider = provider,
            modelId = modelId,
            audioDurationMs = audioDurationMs,
            voicedMs = null,
            audioSampleRateHz = null,
            audioChannels = null,
            outputChars = outputChars,
            outputTokens = outputTokens,
            inputTokens = inputTokens,
            totalTokens = totalTokens,
            inputLanguage = inputLanguage,
            detectedLanguage = detectedLanguage,
            totalWallMs = totalWallMs,
            success = success,
            errorKind = errorKind,
            coldStartKind = coldStartKind,
            thermalStatus = device.thermalStatus,
            batteryPct = device.batteryPct,
            batteryCharging = device.batteryCharging,
            networkType = device.networkType,
            deviceModel = device.deviceModel,
            osVersion = device.osVersion,
            appVersionCode = device.appVersionCode,
            modelSizeBytes = null,
            retryOf = retryOf
        )
        repo.recordSession(session, phaseRows)
    }

    companion object {
        fun start(
            sessionType: SessionType,
            provider: String,
            modelId: String,
            coldStartKind: ColdStartKind,
            device: DeviceSnapshot,
            inputLanguage: String?
        ): SessionTimer = SessionTimer(
            sessionId = UUID.randomUUID().toString(),
            startedAtWallMs = System.currentTimeMillis(),
            startedAtElapsedMs = SystemClock.elapsedRealtime(),
            sessionType = sessionType,
            provider = provider,
            modelId = modelId,
            coldStartKind = coldStartKind,
            device = device,
            inputLanguage = inputLanguage
        )
    }
}
