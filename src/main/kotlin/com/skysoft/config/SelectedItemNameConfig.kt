package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.HudPosition
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class SelectedItemNameConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Customize the selected item name above the hotbar.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Selected item name settings.")
    @field:Accordion
    val settings = SelectedItemNameSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Selected item name visual details.")
    @field:Accordion
    val details = SelectedItemNameDetailsConfig()

    @JvmField
    @field:Expose
    val position = defaultSelectedItemNamePosition().rememberDefault()

    fun repairLoadedValues() {
        position.rememberDefault(defaultSelectedItemNamePosition())
        details.backgroundOpacity = details.backgroundOpacity.coerceIn(MIN_BACKGROUND_OPACITY, MAX_BACKGROUND_OPACITY)
    }

    private companion object {
        const val MIN_BACKGROUND_OPACITY = 0
        const val MAX_BACKGROUND_OPACITY = 100
    }
}

private fun defaultSelectedItemNamePosition() = HudPosition(0, DEFAULT_POSITION_Y, centerX = true, centerY = false)

private const val DEFAULT_POSITION_Y = -47

class SelectedItemNameSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Always Visible", desc = "Keep the selected item name visible while an item is held.")
    @field:ConfigEditorBoolean
    var alwaysVisible = false
}

class SelectedItemNameDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Background", desc = "Draw a background behind the selected item name.")
    @field:ConfigEditorBoolean
    var background = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Rounded Corners", desc = "Round the background corners.")
    @field:ConfigEditorBoolean
    var roundedCorners = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Background Opacity", desc = "Opacity of the selected item name background.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 1f)
    var backgroundOpacity = DEFAULT_BACKGROUND_OPACITY

    private companion object {
        const val DEFAULT_BACKGROUND_OPACITY = 63
    }
}
