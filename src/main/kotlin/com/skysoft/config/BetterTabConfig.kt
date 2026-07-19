package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class BetterTabConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Replace the Hypixel SkyBlock tab list with a compact layout.")
    @field:ConfigEditorBoolean
    var isEnabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Better TAB settings.")
    @field:Accordion
    val settings = BetterTabSettingsConfig()
}

class BetterTabSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hide Second Player Column", desc = "Hide Hypixel's duplicate second Players column.")
    @field:ConfigEditorBoolean
    var isSecondPlayerColumnHidden = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Player Heads", desc = "Show player heads beside player names.")
    @field:ConfigEditorBoolean
    var arePlayerHeadsShown = true
}
