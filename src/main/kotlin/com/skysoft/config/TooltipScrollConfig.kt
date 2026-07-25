package com.skysoft.config

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.skysoft.config.core.ConfigRepairable
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import org.lwjgl.glfw.GLFW

class TooltipScrollConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Allow tooltips to be moved with the mouse wheel and movement keys.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Tooltip movement controls.")
    @field:Accordion
    val settings = TooltipScrollSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Tooltip movement behavior and animation.")
    @field:Accordion
    val details = TooltipScrollDetailsConfig()

    override fun repairLoadedValues() {
        settings.mouseScrollingSpeed = settings.mouseScrollingSpeed.coerceIn(
            MIN_TOOLTIP_SCROLL_SPEED,
            MAX_TOOLTIP_SCROLL_SPEED,
        )
        settings.keyboardScrollingSpeed = settings.keyboardScrollingSpeed.coerceIn(
            MIN_TOOLTIP_SCROLL_SPEED,
            MAX_TOOLTIP_SCROLL_SPEED,
        )
        details.scrollSmoothness = details.scrollSmoothness.coerceIn(
            MIN_TOOLTIP_SCROLL_SMOOTHNESS,
            MAX_TOOLTIP_SCROLL_SMOOTHNESS,
        )
    }
}

class TooltipScrollSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enable Scroll Wheel", desc = "Move tooltips with the mouse wheel.")
    @field:ConfigEditorBoolean
    var enableScrollWheel = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enable in Chat", desc = "Allow tooltip movement while chat is open.")
    @field:ConfigEditorBoolean
    var isEnabledInChat = false

    @JvmField
    @field:Expose
    @field:SerializedName(value = "interfaceScrollTooltipKey", alternate = ["storageOverlayTooltipKey"])
    @field:ConfigOption(
        name = "Interface Scroll Tooltip Key",
        desc = "Hold this key to scroll a tooltip instead of the hovered interface.",
    )
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_LEFT_SHIFT)
    var interfaceScrollTooltipKey = GLFW.GLFW_KEY_LEFT_SHIFT

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enable WASD", desc = "Use WASD to move the hovered tooltip.")
    @field:ConfigEditorBoolean
    var enableWASD = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Mouse Scrolling Speed", desc = "Pixels moved per mouse-wheel step.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 40f, minStep = 1f)
    var mouseScrollingSpeed = DEFAULT_TOOLTIP_SCROLL_SPEED

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Keyboard Scrolling Speed", desc = "Pixels moved per tick while a tooltip movement key is held.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 40f, minStep = 1f)
    var keyboardScrollingSpeed = DEFAULT_TOOLTIP_KEYBOARD_SCROLL_SPEED

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Move Up Key", desc = "Move the hovered tooltip up.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_PAGE_UP)
    var moveUpKey = GLFW.GLFW_KEY_PAGE_UP

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Move Down Key", desc = "Move the hovered tooltip down.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_PAGE_DOWN)
    var moveDownKey = GLFW.GLFW_KEY_PAGE_DOWN

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Horizontal Movement Key", desc = "Hold this key to make up and down movement horizontal.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var horizontalMovementKey = GLFW.GLFW_KEY_UNKNOWN

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Reset Tooltip Key", desc = "Reset the hovered tooltip's moved position.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var resetTooltipKey = GLFW.GLFW_KEY_UNKNOWN
}

class TooltipScrollDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Start On Top", desc = "Show the top of oversized tooltips when they first appear.")
    @field:ConfigEditorBoolean
    var startOnTop = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Reset Position When Not Hovered", desc = "Reset tooltip movement after the tooltip disappears.")
    @field:ConfigEditorBoolean
    var resetPositionWhenNotHovered = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Use Left Shift", desc = "Hold left shift to move tooltips horizontally with the mouse wheel.")
    @field:ConfigEditorBoolean
    var useLeftShift = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Invert Horizontal Movement", desc = "Invert horizontal tooltip movement.")
    @field:ConfigEditorBoolean
    var invertHorizontalMovement = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Invert Vertical Movement", desc = "Invert vertical tooltip movement.")
    @field:ConfigEditorBoolean
    var invertVerticalMovement = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Scroll Smoothness", desc = "How quickly tooltips slide toward the moved position. 100 is instant.")
    @field:ConfigEditorSlider(minValue = 5f, maxValue = 100f, minStep = 5f)
    var scrollSmoothness = DEFAULT_TOOLTIP_SCROLL_SMOOTHNESS
}

const val MIN_TOOLTIP_SCROLL_SPEED = 1

const val MAX_TOOLTIP_SCROLL_SPEED = 40

const val DEFAULT_TOOLTIP_SCROLL_SPEED = 10

const val DEFAULT_TOOLTIP_KEYBOARD_SCROLL_SPEED = 5

const val MIN_TOOLTIP_SCROLL_SMOOTHNESS = 5

const val MAX_TOOLTIP_SCROLL_SMOOTHNESS = 100

const val DEFAULT_TOOLTIP_SCROLL_SMOOTHNESS = 25
