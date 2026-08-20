package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.HudPosition
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorInfoText
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf

class SackHudConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show a movable HUD of selected sack item quantities.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Sack HUD settings.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val settings = SackHudSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Sack HUD appearance.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val details = SackHudDetailsConfig()

    @JvmField
    @field:Expose
    val trackedItems: MutableList<String> = mutableListOf()

    @JvmField
    @field:Expose
    val position = HudPosition(8, 110, centerX = false, centerY = false).rememberDefault()

    override fun repairLoadedValues() {
        settings.maximumItems = settings.maximumItems.coerceIn(MINIMUM_ITEMS, MAXIMUM_ITEMS)
        val repaired = trackedItems.asSequence().map(String::trim).filter(String::isNotEmpty).distinct().toList()
        trackedItems.clear()
        trackedItems.addAll(repaired)
    }
}

class SackHudSettingsConfig {
    @JvmField
    @field:ConfigOption(
        name = "Tracked Items",
        desc = "Open any inventory, then use §e[+ Add Item]§7 on the HUD and click an inventory item.\n" +
            "§eRight-click§7 a row to remove it. Amounts update from sack menus and §e[Sacks]§7 chat messages.",
    )
    @field:ConfigEditorInfoText
    val trackedItemsHelp: Unit = Unit

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Maximum Items", desc = "Maximum tracked sack item rows shown at once.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 20f, minStep = 1f)
    var maximumItems = 8

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Only in Menus", desc = "Only show the Sack HUD while a container menu is open.")
    @field:ConfigEditorBoolean
    var isOnlyInMenus = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hide When Empty", desc = "Hide the Sack HUD when no items are being tracked.")
    @field:ConfigEditorBoolean
    var hideWhenEmpty = false

    @JvmField
    @field:ConfigOption(name = "Clear Tracked Items", desc = "Remove every item from the Sack HUD.")
    @field:ConfigEditorButton(buttonText = "Clear")
    val clearTrackedItems = Runnable {
        val config = SkysoftConfigGui.config().inventory.sackHud
        if (config.trackedItems.isEmpty()) return@Runnable
        config.trackedItems.clear()
        SkysoftConfigGui.config().saveNow()
    }
}

class SackHudDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Title", desc = "Show the §eSack HUD§7 title above tracked items.")
    @field:ConfigEditorBoolean
    var showTitle = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Item Names", desc = "Show item names beside tracked sack amounts.")
    @field:ConfigEditorBoolean
    var showItemNames = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Item Icons", desc = "Show item icons beside tracked sack amounts.")
    @field:ConfigEditorBoolean
    var showItemIcons = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Background", desc = "Draw a dark background behind the Sack HUD.")
    @field:ConfigEditorBoolean
    var showBackground = true

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Highlight Changes",
        desc = "Briefly highlight amounts when §e[Sacks]§7 chat updates them.",
    )
    @field:ConfigEditorBoolean
    var highlightChanges = true
}

private const val MINIMUM_ITEMS = 1
private const val MAXIMUM_ITEMS = 20
