package com.hyperwhisper

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HyperWhisperApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
