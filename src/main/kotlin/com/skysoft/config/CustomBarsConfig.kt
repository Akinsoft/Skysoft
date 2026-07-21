package com.skysoft.config

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.skysoft.config.core.HudPosition
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class CustomBarsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Replace SkyBlock status displays with custom bars.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Choose which bars and readouts are shown.")
    @field:Accordion
    val settings = CustomBarsSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Customize the Custom Bars appearance.")
    @field:Accordion
    val details = CustomBarsDetailsConfig()

    @JvmField
    @field:Expose
    val healthPosition = defaultHealthPosition().rememberDefault()

    @JvmField
    @field:Expose
    val manaPosition = defaultManaPosition().rememberDefault()

    @JvmField
    @field:Expose
    val experiencePosition = defaultExperiencePosition().rememberDefault()

    @JvmField
    @field:Expose
    val defensePosition = defaultDefensePosition().rememberDefault()

    @JvmField
    @field:Expose
    val speedPosition = defaultSpeedPosition().rememberDefault()

    @JvmField
    @field:Expose
    val airPosition = defaultAirPosition().rememberDefault()

    fun repairLoadedValues() {
        healthPosition.rememberDefault(defaultHealthPosition())
        manaPosition.rememberDefault(defaultManaPosition())
        experiencePosition.rememberDefault(defaultExperiencePosition())
        defensePosition.rememberDefault(defaultDefensePosition())
        speedPosition.rememberDefault(defaultSpeedPosition())
        airPosition.rememberDefault(defaultAirPosition())
    }
}

class CustomBarsSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Health", desc = "Show the Health bar.")
    @field:ConfigEditorBoolean
    var health = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Mana", desc = "Show the Mana bar.")
    @field:ConfigEditorBoolean
    var mana = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Experience", desc = "Show the Experience bar.")
    @field:ConfigEditorBoolean
    var experience = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Defense", desc = "Show the Defense readout.")
    @field:ConfigEditorBoolean
    var defense = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Speed", desc = "Show the Speed readout.")
    @field:ConfigEditorBoolean
    var speed = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Air", desc = "Show remaining air while underwater.")
    @field:ConfigEditorBoolean
    var air = true
}

class CustomBarsDetailsConfig {
    @JvmField
    @field:Expose
    @field:SerializedName(value = "textOutline", alternate = ["textShadow"])
    @field:ConfigOption(name = "Text Outline", desc = "Draw a vanilla-style outline around bar and readout text.")
    @field:ConfigEditorBoolean
    var textOutline = true
}

private val healthPositionDefault = HudPosition(-46, -35, centerX = true, centerY = false)
private val manaPositionDefault = HudPosition(47, -35, centerX = true, centerY = false)
private val experiencePositionDefault = HudPosition(0, -24, centerX = true, centerY = false)
private val defensePositionDefault = HudPosition(117, -34, centerX = true, centerY = false)
private val speedPositionDefault = HudPosition(117, -23, centerX = true, centerY = false)
private val airPositionDefault = HudPosition(117, -12, centerX = true, centerY = false)

private fun defaultHealthPosition() = healthPositionDefault.copy()
private fun defaultManaPosition() = manaPositionDefault.copy()
private fun defaultExperiencePosition() = experiencePositionDefault.copy()
private fun defaultDefensePosition() = defensePositionDefault.copy()
private fun defaultSpeedPosition() = speedPositionDefault.copy()
private fun defaultAirPosition() = airPositionDefault.copy()

private fun HudPosition.copy() = HudPosition(x, y, scale, centerX, centerY)
