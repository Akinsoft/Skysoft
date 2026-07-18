package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.HudPosition
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

enum class ServerInfoMetric(private val displayName: String) {
    FPS("FPS"),
    TPS("TPS"),
    PING("Ping"),
    ;

    override fun toString(): String = displayName
}

class ServerInfoDisplayConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show server performance information.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Choose and order displayed information.")
    @field:Accordion
    val settings = ServerInfoDisplaySettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Customize the display appearance.")
    @field:Accordion
    val details = ServerInfoDisplayDetailsConfig()

    @JvmField
    @field:Expose
    val position = HudPosition(8, 30, centerY = false).rememberDefault()
}

class ServerInfoDisplaySettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Displayed Info", desc = "Server information shown from top to bottom.")
    @field:ConfigEditorDraggableList
    val metrics: Property<MutableList<ServerInfoMetric>> = Property.of(
        mutableListOf(ServerInfoMetric.FPS, ServerInfoMetric.TPS, ServerInfoMetric.PING),
    )
}

class ServerInfoDisplayDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Background", desc = "Draw a background behind the server information.")
    @field:ConfigEditorBoolean
    var background = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Color", desc = "Color used for the server information text.")
    @field:ConfigEditorColour
    val color: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 255, 255, 0, 255))
}
