package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.data.hypixel.SkysoftGame.SKYBLOCK
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class EnchantingFeatureConfig {
    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:ConfigOption(
        name = "Experimentation Helper",
        desc = "Show Chronomatron notes, reveal Ultrasequencer numbers, and keep seen Superpairs rewards visible.",
    )
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var isExperimentationTableHelperEnabled = false
}
