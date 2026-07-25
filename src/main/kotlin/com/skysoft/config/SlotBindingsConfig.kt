package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.features.inventory.SlotBindingManager
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property
import org.lwjgl.glfw.GLFW

class SlotBindingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Enable slot bindings.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Slot binding controls.")
    @field:Accordion
    val settings = SlotBindingsSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Slot binding visual settings.")
    @field:Accordion
    val details = SlotBindingsDetailsConfig()
}

class SlotBindingsSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Binding Key",
        desc = "Hold this key over an inventory slot, move to another slot, and release to bind. Tap over a bound slot to unbind.",
    )
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_B)
    var bindingKey = GLFW.GLFW_KEY_B

    @JvmField
    @field:ConfigOption(name = "Reset All Bindings", desc = "Remove all slot bindings.")
    @field:ConfigEditorButton(buttonText = "Reset")
    val resetAllBindings = Runnable { SlotBindingManager.resetAllBindings() }
}

class SlotBindingsDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Highlights", desc = "Draw highlights and lines for bound slots.")
    @field:ConfigEditorBoolean
    var showHighlights = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Highlight Color", desc = "Color used for bound slot outlines and lines.")
    @field:ConfigEditorColour
    val highlightColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(48, 128, 255, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Highlight Style", desc = "Choose whether bound slots are filled or only outlined.")
    @field:ConfigEditorDropdown
    var highlightStyle = SlotBindingHighlightStyle.FILL

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Shift Hover Highlight",
        desc = "While holding Shift over a bound slot, highlight its paired slot in white.",
    )
    @field:ConfigEditorBoolean
    var showShiftHoverHighlight = true
}
