package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class CombatFeatureConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Better Shurikens", desc = "Show Shuriken tags at mobs' feet.")
    @field:ConfigEditorBoolean
    var isBetterShurikensEnabled = false
}
