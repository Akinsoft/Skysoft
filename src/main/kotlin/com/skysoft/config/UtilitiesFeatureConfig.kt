package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.data.hypixel.SkysoftGame.SKYBLOCK
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf

class UtilitiesFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "Auto Sprint", desc = "Automatically sprint under configurable conditions.")
    val autoSprint = AutoSprintConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:ConfigOption(
        name = "Short Warp Commands",
        desc = "Use warp names such as /garden and /crypts without typing /warp.",
    )
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var shortWarpCommands = false

    @JvmField
    @field:Expose
    @field:Category(name = "Screenshot Manager", desc = "Browse and manage Minecraft screenshots.")
    val screenshotManager = ScreenshotManagerConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Death Sounds", desc = "Play a sound when you die in SkyBlock.")
    val deathSounds = DeathSoundsConfig()
}

class DeathSoundsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Play a sound when you die in SkyBlock.")
    @field:ConfigEditorBoolean
    var enabled = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Death sound settings.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val settings = DeathSoundsSettingsConfig()
}

class DeathSoundsSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Volume", desc = "Volume of the death sound.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 1f)
    var volume = 50
}
