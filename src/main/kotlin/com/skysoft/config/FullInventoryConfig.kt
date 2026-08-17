package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class FullInventoryConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show a warning when your inventory reaches the configured empty slot limit.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Full inventory warning settings.")
    @field:Accordion
    val settings = FullInventorySettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Full inventory alert details.")
    @field:Accordion
    val details = FullInventoryDetailsConfig()

    override fun repairLoadedValues() {
        settings.emptySlots = settings.emptySlots.coerceIn(
            MIN_FULL_INVENTORY_EMPTY_SLOTS,
            MAX_FULL_INVENTORY_EMPTY_SLOTS,
        )
    }
}

class FullInventorySettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Empty Slots",
        desc = "Warn when your inventory has this many empty slots or less. 0 means only when full.",
    )
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 36f, minStep = 1f)
    var emptySlots = DEFAULT_FULL_INVENTORY_EMPTY_SLOTS
}

class FullInventoryDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Play Sound", desc = "Play a sound when the warning triggers.")
    @field:ConfigEditorBoolean
    var playSound = true
}

const val MIN_FULL_INVENTORY_EMPTY_SLOTS = 0

const val MAX_FULL_INVENTORY_EMPTY_SLOTS = 36

const val DEFAULT_FULL_INVENTORY_EMPTY_SLOTS = 0
