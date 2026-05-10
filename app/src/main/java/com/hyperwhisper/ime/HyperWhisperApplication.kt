package com.hyperwhisper

import android.app.Application
import com.hyperwhisper.data.telemetry.PerformanceRepository
import com.hyperwhisper.utils.CrashHandler
import com.hyperwhisper.utils.TraceLogger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HyperWhisperApplication : Application() {

    @Inject lateinit var performanceRepository: PerformanceRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Initialize trace logger (clears log from previous session)
        TraceLogger.init(this)
        TraceLogger.lifecycle("Application", "onCreate", "App starting")

        // Install global crash handler
        CrashHandler.install(this)
        TraceLogger.trace("Application", "Crash handler installed")

        // Prune telemetry older than 90 days off the main thread
        appScope.launch {
            try {
                val pruned = performanceRepository.pruneOlderThan90Days()
                if (pruned > 0) TraceLogger.trace("Application", "Pruned $pruned old telemetry rows")
            } catch (t: Throwable) {
                TraceLogger.trace("Application", "telemetry prune failed: ${t.message}")
            }
        }
    }
}
