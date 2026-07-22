package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.HudPosition
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf
import io.github.notenoughupdates.moulconfig.observer.Property

class InventoryHudConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show your inventory as part of the in-game HUD.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Choose what the Inventory HUD shows.")
    @field:Accordion
    val settings = InventoryHudSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Customize the Inventory HUD appearance.")
    @field:Accordion
    val details = InventoryHudDetailsConfig()

    @JvmField
    @field:Expose
    val position = defaultInventoryHudPosition().rememberDefault()

    fun repairLoadedValues() {
        position.rememberDefault(defaultInventoryHudPosition())
    }
}

class InventoryHudSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show in Screens", desc = "Keep the Inventory HUD visible while a screen is open.")
    @field:ConfigEditorBoolean
    var showInScreens = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Armor", desc = "Show worn armor on the left.")
    @field:ConfigEditorBoolean
    var armor = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Equipment", desc = "Show cached SkyBlock equipment on the right.")
    @field:ConfigEditorBoolean
    var equipment = true
}

class InventoryHudDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Background", desc = "Draw backgrounds behind each inventory section.")
    @field:ConfigEditorBoolean
    var background = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Background Color", desc = "Color used for inventory section backgrounds.")
    @field:ConfigEditorColour
    @field:ConfigVisibleIf("background")
    val backgroundColor: Property<ChromaColour> =
        Property.of(ChromaColour.fromRGB(16, 16, 16, 0, 176))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Outline", desc = "Draw outlines around each inventory section.")
    @field:ConfigEditorBoolean
    var outline = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Outline Color", desc = "Color used for inventory section outlines.")
    @field:ConfigEditorColour
    @field:ConfigVisibleIf("outline")
    val outlineColor: Property<ChromaColour> =
        Property.of(ChromaColour.fromRGB(80, 80, 80, 0, 192))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Slot Backgrounds", desc = "Draw backgrounds behind inventory items.")
    @field:ConfigEditorBoolean
    var slotBackgrounds = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Slot Background Color", desc = "Color used behind inventory items.")
    @field:ConfigEditorColour
    @field:ConfigVisibleIf("slotBackgrounds")
    val slotBackgroundColor: Property<ChromaColour> =
        Property.of(ChromaColour.fromRGB(30, 30, 36, 0, 208))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Rounded Corners", desc = "Round inventory section and slot corners.")
    @field:ConfigEditorBoolean
    var roundedCorners = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Item Count Shadow", desc = "Draw shadows behind item counts.")
    @field:ConfigEditorBoolean
    var itemCountShadow = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Item Count Color", desc = "Color used for item counts.")
    @field:ConfigEditorColour
    val itemCountColor: Property<ChromaColour> =
        Property.of(ChromaColour.fromRGB(255, 255, 255, 0, 255))
}

private fun defaultInventoryHudPosition() =
    HudPosition(0, DEFAULT_INVENTORY_HUD_BOTTOM_MARGIN, centerX = true, centerY = false)

private const val DEFAULT_INVENTORY_HUD_BOTTOM_MARGIN = -4
