package com.hyperwhisper.data.config

import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.AppearanceSettings
import com.hyperwhisper.data.VoiceMode

/** Shared snapshot builders for the config test suite. */
object ConfigTestFixtures {

    fun defaultVoiceModes(): List<VoiceMode> = listOf(
        VoiceMode(id = "verbatim", name = "Verbatim"),
        VoiceMode(id = "configuration", name = "Configuration"),
    )

    fun defaultSnapshot(): ConfigSnapshot = ConfigSnapshot(
        api = ApiSettings(),
        appearance = AppearanceSettings(),
        voiceModes = defaultVoiceModes(),
        selectedModeId = "verbatim",
    )
}
