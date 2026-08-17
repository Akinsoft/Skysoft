package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class InventoryEquipmentConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show cached equipment beside your inventory.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Click Action", desc = "Choose what happens when clicking an inventory equipment slot.")
    @field:ConfigEditorDropdown
    var clickAction = InventoryEquipmentClickAction.STATS
}

enum class InventoryEquipmentClickAction(private val displayName: String, val command: String?) {
    NOTHING("Nothing", null),
    STATS("/stats", "stats"),
    EQUIPMENT("/equipment", "equipment"),
    LOADOUT("/loadout", "loadout"),
    ;

    override fun toString(): String = displayName
}
