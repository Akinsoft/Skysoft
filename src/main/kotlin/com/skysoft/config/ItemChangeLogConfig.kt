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

class ItemChangeLogConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show recent inventory item gains and losses.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Item Change Log settings.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val settings = ItemChangeLogSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Item Change Log appearance.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val details = ItemChangeLogDetailsConfig()

    @JvmField
    @field:Expose
    val position = HudPosition(8, 110, centerY = false).rememberDefault()
}

class ItemChangeLogSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Invert Direction", desc = "Grow new item changes downward from the anchor.")
    @field:ConfigEditorBoolean
    var invertDirection = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Display Time", desc = "Seconds before an item change disappears.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 20f, minStep = 1f)
    var displaySeconds = 8

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Maximum Lines", desc = "Maximum item changes shown at once.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 10f, minStep = 1f)
    var maximumLines = 5
}

class ItemChangeLogDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Gain Color", desc = "Color used for gained items.")
    @field:ConfigEditorColour
    val gainColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(85, 255, 85, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Loss Color", desc = "Color used for lost items.")
    @field:ConfigEditorColour
    val lossColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 85, 85, 0, 255))
}
