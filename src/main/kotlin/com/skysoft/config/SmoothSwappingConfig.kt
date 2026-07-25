package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class SmoothSwappingConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Animate item movement inside inventory screens.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Item movement animation settings.")
    @field:Accordion
    val settings = SmoothSwappingSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Item movement animation details.")
    @field:Accordion
    val details = SmoothSwappingDetailsConfig()

    override fun repairLoadedValues() {
        settings.animationSpeed = settings.animationSpeed.coerceIn(
            MIN_SMOOTH_SWAPPING_SPEED,
            MAX_SMOOTH_SWAPPING_SPEED,
        )
    }
}

class SmoothSwappingSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Animation Speed", desc = "How quickly items move between slots. 100 matches the default speed.")
    @field:ConfigEditorSlider(minValue = 25f, maxValue = 300f, minStep = 5f)
    var animationSpeed = DEFAULT_SMOOTH_SWAPPING_SPEED
}

class SmoothSwappingDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Animation Curve", desc = "Choose how item movement accelerates and slows down.")
    @field:ConfigEditorDropdown
    var animationCurve = SmoothSwappingCurve.EASE_IN_OUT
}

enum class SmoothSwappingCurve(private val displayName: String) {
    LINEAR("Linear"),
    EASE_OUT("Ease Out"),
    EASE_IN_OUT("Ease In Out"),
    ;

    override fun toString(): String = displayName
}

const val MIN_SMOOTH_SWAPPING_SPEED = 25

const val MAX_SMOOTH_SWAPPING_SPEED = 300

const val DEFAULT_SMOOTH_SWAPPING_SPEED = 125

const val DEFAULT_SMOOTH_SWAPPING_DURATION = 180
