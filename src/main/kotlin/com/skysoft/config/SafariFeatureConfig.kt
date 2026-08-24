package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class SafariFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "Honeybug Helper", desc = "Find Honeybugs in the Critter Safari.")
    val honeybugHelper = HoneybugHelperConfig()
}

class HoneybugHelperConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Highlight unsearched beehives in the Critter Safari.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Honeybug Helper appearance.")
    @field:Accordion
    val details = HoneybugHelperDetailsConfig()
}

class HoneybugHelperDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Crosshair Line", desc = "Draw a line to the nearest Honeybug.")
    @field:ConfigEditorBoolean
    var crosshairLine = true
}
