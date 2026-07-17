package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.HudPosition
import com.skysoft.data.SkyBlockIsland
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

class DayDisplayConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show the current Minecraft day.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Choose where the display appears.")
    @field:Accordion
    val settings = DayDisplaySettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Customize the display appearance.")
    @field:Accordion
    val details = DayDisplayDetailsConfig()

    @JvmField
    @field:Expose
    val position = HudPosition(8, 8, centerY = false).rememberDefault()
}

class DayDisplaySettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Islands", desc = "SkyBlock islands where the Day Display appears.")
    @field:ConfigEditorDraggableList
    val islands: Property<MutableList<SkyBlockIsland>> = Property.of(SkyBlockIsland.entries.toMutableList())
}

class DayDisplayDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Background", desc = "Draw a background behind the day.")
    @field:ConfigEditorBoolean
    var background = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Color", desc = "Color used for the day text.")
    @field:ConfigEditorColour
    val color: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 255, 255, 0, 255))
}
