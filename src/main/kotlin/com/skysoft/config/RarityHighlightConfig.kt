package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class RarityHighlightConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Highlight inventory items by rarity.")
    @field:ConfigEditorBoolean
    var isEnabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Rarity highlight controls.")
    @field:Accordion
    val settings = RarityHighlightSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Rarity highlight appearance.")
    @field:Accordion
    val details = RarityHighlightDetailsConfig()

    override fun repairLoadedValues() {
        details.opacity = details.opacity.coerceIn(
            RarityHighlightDetailsConfig.MIN_OPACITY,
            RarityHighlightDetailsConfig.MAX_OPACITY,
        )
    }
}

class RarityHighlightSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Type", desc = "Choose the highlight shape.")
    @field:ConfigEditorDropdown
    var type = RarityHighlightType.SQUARE
}

class RarityHighlightDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Opacity", desc = "Opacity of rarity highlights.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 5f)
    var opacity = 40

    companion object {
        const val MIN_OPACITY = 0
        const val MAX_OPACITY = 100
    }
}

enum class RarityHighlightType(private val displayName: String) {
    ROUND("Round"),
    SQUARE("Square"),
    CONTOUR("Contour"),
    ;

    override fun toString(): String = displayName
}
