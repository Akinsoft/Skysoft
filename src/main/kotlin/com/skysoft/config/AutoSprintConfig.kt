package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorCombinations
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorInfoText
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class AutoSprintConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Automatically sprint while moving.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Auto Sprint settings.")
    @field:Accordion
    val settings = AutoSprintSettingsConfig()
}

class AutoSprintSettingsConfig {
    @JvmField
    @field:ConfigOption(
        name = "Item Conditions",
        desc = "Use §b/ss autosprint additem§7 while holding a SkyBlock item to add it as a condition.",
    )
    @field:ConfigEditorInfoText
    val itemConditionInfo: Unit = Unit

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Reverse Conditions", desc = "Matching combinations disable Auto Sprint instead.")
    @field:ConfigEditorBoolean
    var isConditionActivationReversed = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Combinations", desc = "Any matching combination enables Auto Sprint.")
    @field:ConfigEditorCombinations(provider = AutoSprintCombinationsProvider::class)
    val combinations: MutableList<FeatureConditionCombination> = mutableListOf()
}
