package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class CombatFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "Cocoon Display", desc = "Show cocooned mob names and hatch timers in the world.")
    val cocoonDisplay = CocoonDisplayConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Better Shurikens", desc = "Show Shuriken tags at mobs' feet.")
    @field:ConfigEditorBoolean
    var isBetterShurikensEnabled = false
}

class CocoonDisplayConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show cocooned mob names and hatch timers in the world.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Cocoon display settings.")
    @field:Accordion
    val settings = CocoonDisplaySettingsConfig()
}

class CocoonDisplaySettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Only Slayer Targets",
        desc = "During active Slayer quests, only show bosses and mini-bosses.",
    )
    @field:ConfigEditorBoolean
    var onlySlayerTargets = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Timer Prefix", desc = "Show \"Hatches in\" before the cocoon timer.")
    @field:ConfigEditorBoolean
    var showTimerPrefix = true
}
