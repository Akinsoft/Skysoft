package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.HudPosition
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class PartyDisplayConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show party members with their player heads and rank colors.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Party Display settings.")
    @field:Accordion
    val settings = PartyDisplaySettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Customize the display appearance.")
    @field:Accordion
    val details = PartyDisplayDetailsConfig()

    @JvmField
    @field:Expose
    val position = HudPosition(8, 8, centerY = false).rememberDefault()
}

class PartyDisplaySettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Lootshare Display",
        desc = "Show loot eligibility checkmarks next to names from supported features like Diana's Event.",
    )
    @field:ConfigEditorBoolean
    var lootshareDisplay = true
}

class PartyDisplayDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Alignment", desc = "Align the title and party members.")
    @field:ConfigEditorDropdown
    var alignment = PartyDisplayAlignment.LEFT

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Background", desc = "Draw a background behind the party display.")
    @field:ConfigEditorBoolean
    var background = false
}

enum class PartyDisplayAlignment(private val displayName: String) {
    LEFT("Left"),
    CENTER("Center"),
    RIGHT("Right"),
    ;

    override fun toString(): String = displayName
}
