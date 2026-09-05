package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf
import org.lwjgl.glfw.GLFW

class ZoomConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Zoom the camera with a configurable key.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Zoom controls and behavior.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val settings = ZoomSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Zoom camera details.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val details = ZoomDetailsConfig()

    override fun repairLoadedValues() {
        settings.zoomAmount = settings.zoomAmount.coerceIn(MIN_ZOOM_AMOUNT, MAX_ZOOM_AMOUNT)
        details.transitionMillis = details.transitionMillis.coerceIn(MIN_ZOOM_TRANSITION, MAX_ZOOM_TRANSITION)
    }
}

class ZoomSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Zoom Key", desc = "Key used to zoom the camera.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_C)
    var key = GLFW.GLFW_KEY_C

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Activation", desc = "Choose whether the zoom key is held or toggled.")
    @field:ConfigEditorDropdown
    var activation = ZoomActivation.HOLD

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Zoom Amount", desc = "Initial camera magnification.")
    @field:ConfigEditorSlider(minValue = 2f, maxValue = 16f, minStep = 0.5f)
    var zoomAmount = DEFAULT_ZOOM_AMOUNT

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Scroll to Adjust", desc = "Adjust magnification with the scroll wheel while zooming.")
    @field:ConfigEditorBoolean
    var scrollToAdjust = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Remember Adjustment", desc = "Keep the adjusted magnification after zooming.")
    @field:ConfigEditorBoolean
    var rememberAdjustment = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Relative Sensitivity", desc = "Reduce camera movement in proportion to magnification.")
    @field:ConfigEditorBoolean
    var relativeSensitivity = true
}

class ZoomDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hide Hand", desc = "Hide your hands and held items while zooming.")
    @field:ConfigEditorBoolean
    var hideHand = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Smooth Transition", desc = "Ease the camera in and out of zoom.")
    @field:ConfigEditorBoolean
    var smoothTransition = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Transition Time", desc = "Milliseconds used to enter and leave zoom.")
    @field:ConfigEditorSlider(minValue = 50f, maxValue = 500f, minStep = 25f)
    var transitionMillis = DEFAULT_ZOOM_TRANSITION
}

enum class ZoomActivation(private val displayName: String) {
    HOLD("Hold"),
    TOGGLE("Toggle"),
    ;

    override fun toString(): String = displayName
}

const val MIN_ZOOM_AMOUNT = 2
const val MAX_ZOOM_AMOUNT = 16
const val DEFAULT_ZOOM_AMOUNT = 4
const val MIN_ZOOM_TRANSITION = 50
const val MAX_ZOOM_TRANSITION = 500
const val DEFAULT_ZOOM_TRANSITION = 175
