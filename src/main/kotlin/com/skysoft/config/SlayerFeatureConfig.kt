package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class SlayerFeatureConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Miniboss Alert", desc = "Show a title and play a sound when a Slayer miniboss spawns.")
    @field:ConfigEditorBoolean
    var minibossAlert = false
}
