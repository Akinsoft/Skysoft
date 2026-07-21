package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.HudPosition
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

class MouseLockConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Mouse Lock settings.")
    @field:Accordion
    val settings = MouseLockSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Customize the Mouse Lock display.")
    @field:Accordion
    val details = MouseLockDetailsConfig()

    @JvmField
    @field:Expose
    val position = HudPosition(8, 92, centerY = false).rememberDefault()
}

class MouseLockSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Display", desc = "Show when Mouse Lock is enabled.")
    @field:ConfigEditorBoolean
    var showDisplay = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hide Message", desc = "Hide the chat message when Mouse Lock is toggled.")
    @field:ConfigEditorBoolean
    var hideMessage = false
}

class MouseLockDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Background", desc = "Draw a background behind the Mouse Lock display.")
    @field:ConfigEditorBoolean
    var background = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Color", desc = "Color used for the Mouse Lock text.")
    @field:ConfigEditorColour
    val color: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 255, 255, 0, 255))
}
