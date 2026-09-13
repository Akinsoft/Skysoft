package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.repairLoadedConfigs
import com.skysoft.data.hypixel.SkysoftGame.SKYBLOCK
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf

class InventoryFeatureConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Equipment", desc = "Equipment displays and menu keybinds.")
    val equipment = InventoryEquipmentGroupConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Controls", desc = "Inventory buttons, item movement, and input controls.")
    val controls = InventoryControlsConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Protection & Warnings", desc = "Protect items and warn when inventory space is low.")
    val protection = InventoryProtectionConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Appearance", desc = "Inventory item appearance and screen scaling.")
    val appearance = InventoryAppearanceConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Tooltips", desc = "Tooltip navigation and appearance.")
    val tooltips = InventoryTooltipsConfig()

    override fun repairLoadedValues() = repairLoadedConfigs(controls, protection, appearance, tooltips)
}

class PreserveCursorPositionConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Enabled",
        desc = "Keep the mouse at the same position when Minecraft briefly closes and reopens an inventory.",
    )
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Cursor position preservation settings.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val settings = PreserveCursorPositionSettingsConfig()
}

class PreserveCursorPositionSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Duration", desc = "Seconds to keep a cursor position after its inventory closes.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 10f, minStep = 1f)
    var durationSeconds = 1
}

class MaxEnchantChromaConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show maximum-level enchantments in animated chroma.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Max Enchant Chroma settings.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val settings = MaxEnchantChromaSettingsConfig()
}

class MaxEnchantChromaSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Ultimate Enchantments", desc = "Apply chroma to maximum-level Ultimate Enchantments.")
    @field:ConfigEditorBoolean
    var includeUltimateEnchantments = false
}
