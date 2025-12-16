package com.hyperwhisper

import android.app.Application
import com.hyperwhisper.utils.CrashHandler
import com.hyperwhisper.utils.TraceLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HyperWhisperApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize trace logger (clears log from previous session)
        TraceLogger.init(this)
        TraceLogger.lifecycle("Application", "onCreate", "App starting")

        // Install global crash handler
        CrashHandler.install(this)
        TraceLogger.trace("Application", "Crash handler installed")
    }
}
