package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class SafariFeatureConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Highlight Critters", desc = "Highlight visible critters using their rarity color.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var highlightCritters = false

    @JvmField
    @field:Expose
    @field:Category(name = "Capsule Helper", desc = "Prepare for critters that escape Critter Capsules.")
    val capsuleHelper = CapsuleHelperConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Honeybug Helper", desc = "Find Honeybugs in the Forest.")
    val honeybugHelper = HoneybugHelperConfig()
}

class CapsuleHelperConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Mark where captured critters may reappear.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Capsule Helper appearance.")
    @field:Accordion
    val details = CapsuleHelperDetailsConfig()
}

class CapsuleHelperDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Crosshair Line", desc = "Draw a line to active capture locations.")
    @field:ConfigEditorBoolean
    var crosshairLine = false
}

class HoneybugHelperConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Highlight unsearched beehives in the Forest.")
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
