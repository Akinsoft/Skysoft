package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class HeadDisplaySizeConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Resize player head item icons in inventories and the hotbar.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Head item appearance.")
    @field:Accordion
    val details = HeadDisplaySizeDetailsConfig()

    override fun repairLoadedValues() {
        details.size = details.size.coerceIn(
            HeadDisplaySizeDetailsConfig.MIN_SIZE,
            HeadDisplaySizeDetailsConfig.MAX_SIZE,
        )
    }
}

class HeadDisplaySizeDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Size", desc = "Head item size as a percentage. 100 is the normal size.")
    @field:ConfigEditorSlider(minValue = 50f, maxValue = 200f, minStep = 5f)
    var size = 100

    companion object {
        const val MIN_SIZE = 50
        const val MAX_SIZE = 200
    }
}
