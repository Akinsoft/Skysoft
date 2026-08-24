package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.HudPosition
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf
import io.github.notenoughupdates.moulconfig.observer.Property

class BetterTabConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Replace the Hypixel SkyBlock tab list with a compact layout.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var isEnabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Better TAB settings.")
    @field:Accordion
    val settings = BetterTabSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Customize the Better TAB appearance.")
    @field:Accordion
    val details = BetterTabDetailsConfig()

    @JvmField
    @field:Expose
    val position = defaultBetterTabPosition().rememberDefault()

    fun repairLoadedValues() {
        position.rememberDefault(defaultBetterTabPosition())
    }
}

class BetterTabSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hide Server Address", desc = "Hide the 'You are playing on...' line.")
    @field:ConfigEditorBoolean
    var isServerAddressHidden = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hide Store Banner", desc = "Hide the 'Ranks, Boosters & MORE!' line.")
    @field:ConfigEditorBoolean
    var isStoreBannerHidden = true

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

class BetterTabDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Frames", desc = "Draw SkyBlock-style frames around the TAB and its column panels.")
    @field:ConfigEditorBoolean
    var frames = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Frame Color", desc = "Color used for TAB frames.")
    @field:ConfigVisibleIf("frames")
    @field:ConfigEditorColour
    val frameColor: Property<ChromaColour> =
        Property.of(ChromaColour.fromRGB(108, 106, 113, 0, 79))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Column Panels", desc = "Draw a panel behind each TAB column.")
    @field:ConfigEditorBoolean
    var columnPanels = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Column Panel Color", desc = "Color used for TAB column panels.")
    @field:ConfigVisibleIf("columnPanels")
    @field:ConfigEditorColour
    val columnPanelColor: Property<ChromaColour> =
        Property.of(ChromaColour.fromRGB(80, 80, 80, 0, 37))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Column Padding", desc = "Space between each column's content and frame.")
    @field:ConfigVisibleIf("columnPanels")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 10f, minStep = 1f)
    var columnPadding = 2

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Background Color", desc = "Color used for the TAB background.")
    @field:ConfigEditorColour
    val backgroundColor: Property<ChromaColour> =
        Property.of(ChromaColour.fromRGB(16, 16, 16, 0, 176))
}

private fun defaultBetterTabPosition() =
    HudPosition(0, BETTER_TAB_DEFAULT_TOP_MARGIN, centerX = true, centerY = false)

internal const val BETTER_TAB_DEFAULT_TOP_MARGIN = 7
