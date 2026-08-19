package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.HudPosition
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf

class SackDisplayConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show sack contents beside open sack menus.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Sack Display settings.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val settings = SackDisplaySettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Sack Display appearance.")
    @field:Accordion
    @field:ConfigVisibleIf("enabled")
    val details = SackDisplayDetailsConfig()

    @JvmField
    @field:Expose
    val position = HudPosition(8, 70, centerX = false, centerY = false).rememberDefault()

    override fun repairLoadedValues() {
        settings.maximumItems = settings.maximumItems.coerceIn(MINIMUM_ITEMS, MAXIMUM_ITEMS)
    }
}

class SackDisplaySettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Price Source", desc = "Choose how sack items are valued.")
    @field:ConfigEditorDropdown
    var priceSource = ProfitTrackerPriceSource.INSTANT_SELL

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Maximum Items", desc = "Maximum sack item rows shown at once.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 30f, minStep = 1f)
    var maximumItems = 15
}

class SackDisplayDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Item Icons", desc = "Show item icons beside sack contents.")
    @field:ConfigEditorBoolean
    var showItemIcons = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Background", desc = "Draw a dark background behind the Sack Display.")
    @field:ConfigEditorBoolean
    var showBackground = true
}

private const val MINIMUM_ITEMS = 1
private const val MAXIMUM_ITEMS = 30
