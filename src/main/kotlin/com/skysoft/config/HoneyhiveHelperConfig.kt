package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.HudPosition
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class HoneyhiveHelperConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Track Honeyhive refills and find hives ready to loot.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Honeyhive Helper settings.")
    @field:Accordion
    val settings = HoneyhiveHelperSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Honeyhive display appearance.")
    @field:Accordion
    val details = HoneyhiveHelperDetailsConfig()

    @JvmField
    @field:Expose
    val position = HudPosition(8, 180, centerX = false, centerY = false).rememberDefault()
}

class HoneyhiveHelperSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Display",
        desc = "List Honeyhives by readiness. Open an inventory to scroll or click a hive to toggle its waypoint.",
    )
    @field:ConfigEditorBoolean
    var display = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Show Outside Torrhus", desc = "Show the Honeyhive display on other SkyBlock islands.")
    @field:ConfigEditorBoolean
    var showOutsideTorrhus = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Maximum Lines", desc = "Maximum Honeyhive rows visible at once. Scroll to see the remaining hives.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 30f, minStep = 1f)
    var maximumLines = 12

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Ready Waypoints", desc = "Show waypoints for Honeyhives ready to loot.")
    @field:ConfigEditorBoolean
    var readyWaypoints = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Ready Sound", desc = "Play a sound when Honeyhives become ready in Torrhus Canyon.")
    @field:ConfigEditorBoolean
    var readySound = true
}

class HoneyhiveHelperDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Background", desc = "Show a background behind the Honeyhive display.")
    @field:ConfigEditorBoolean
    var background = true
}
