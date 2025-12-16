package com.hyperwhisper

import android.app.Application
import com.hyperwhisper.utils.CrashHandler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HyperWhisperApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Install global crash handler
        CrashHandler.install(this)
    }
}
