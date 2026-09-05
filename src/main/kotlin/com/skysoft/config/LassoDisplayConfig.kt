package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.HudPosition
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class LassoDisplayConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Move the lasso stamina bar and REEL prompt from above the mob to your screen.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Configure lasso alerts.")
    @field:Accordion
    val settings = LassoDisplaySettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Customize the display appearance.")
    @field:Accordion
    val details = LassoDisplayDetailsConfig()

    @JvmField
    @field:Expose
    val position = HudPosition(0, 40, centerX = true, centerY = true).rememberDefault()
}

class LassoDisplaySettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Reel Sound", desc = "Play a ding when it is time to reel your lasso.")
    @field:ConfigEditorBoolean
    var reelSound = true
}

class LassoDisplayDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Background", desc = "Draw a background behind the lasso display.")
    @field:ConfigEditorBoolean
    var background = false
}
