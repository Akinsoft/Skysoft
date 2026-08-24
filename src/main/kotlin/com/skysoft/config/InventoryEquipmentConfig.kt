package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
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
    @field:ConfigOption(name = "Settings", desc = "Inventory equipment click settings.")
    @field:Accordion
    val settings = InventoryEquipmentSettingsConfig()
}

class InventoryEquipmentSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Click Action", desc = "Choose what happens when clicking an inventory equipment slot.")
    @field:ConfigEditorDropdown
    var clickAction = InventoryEquipmentClickAction.STATS

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Rift Click Action",
        desc = "Choose what happens when clicking an inventory equipment slot inside The Rift.",
    )
    @field:ConfigEditorDropdown
    var riftClickAction = RiftInventoryEquipmentClickAction.STATS
}

enum class InventoryEquipmentClickAction(private val displayName: String, val command: String?) {
    NOTHING("Nothing", null),
    STATS("/stats", "stats"),
    EQUIPMENT("/equipment", "equipment"),
    LOADOUT("/loadout", "loadout"),
    ;

    override fun toString(): String = displayName
}

enum class RiftInventoryEquipmentClickAction(private val displayName: String, val command: String?) {
    NOTHING("Nothing", null),
    STATS("/stats", "stats"),
    ;

    override fun toString(): String = displayName
}
