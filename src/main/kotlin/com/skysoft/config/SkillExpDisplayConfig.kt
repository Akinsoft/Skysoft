package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.HudPosition
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class SkillExpDisplayConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show Skill EXP gains in a separate HUD element.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Skill EXP Display settings.")
    @field:Accordion
    val settings = SkillExpDisplaySettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Customize the display appearance.")
    @field:Accordion
    val details = SkillExpDisplayDetailsConfig()

    @JvmField
    @field:Expose
    val position = HudPosition(0, -76, centerX = true, centerY = false).rememberDefault()
}

class SkillExpDisplaySettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Progress Format", desc = "Show skill progress as numbers or a percentage.")
    @field:ConfigEditorDropdown
    var format = SkillExpProgressFormat.NUMBERS
}

class SkillExpDisplayDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Skill Icon", desc = "Show the skill icon instead of its name.")
    @field:ConfigEditorBoolean
    var showSkillIcon = false
}

enum class SkillExpProgressFormat(private val displayName: String) {
    NUMBERS("Numbers"),
    PERCENTAGE("Percentage"),
    ;

    override fun toString(): String = displayName
}
