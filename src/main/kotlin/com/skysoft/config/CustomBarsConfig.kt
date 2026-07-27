package com.skysoft.config

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.skysoft.config.core.HudDimensions
import com.skysoft.config.core.HudPosition
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigOrder
import io.github.notenoughupdates.moulconfig.observer.Property
import java.awt.Color

class CustomBarsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Customize SkyBlock status displays and numbers.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Choose which bars and numbers are shown.")
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
    val vitalityPosition = defaultVitalityPosition().rememberDefault()

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

    @JvmField
    @field:Expose
    val vanillaHealthPosition = defaultVanillaHealthPosition().rememberDefault()

    @JvmField
    @field:Expose
    val vanillaExperiencePosition = defaultVanillaExperiencePosition().rememberDefault()

    @JvmField
    @field:Expose
    val vanillaAirPosition = defaultVanillaAirPosition().rememberDefault()

    @JvmField
    @field:Expose
    val healthDimensions = HudDimensions()

    @JvmField
    @field:Expose
    val manaDimensions = HudDimensions()

    @JvmField
    @field:Expose
    val vitalityDimensions = HudDimensions()

    @JvmField
    @field:Expose
    val experienceDimensions = HudDimensions()

    @JvmField
    @field:Expose
    val healthTextPosition = defaultTextPosition().rememberDefault()

    @JvmField
    @field:Expose
    val manaTextPosition = defaultTextPosition().rememberDefault()

    @JvmField
    @field:Expose
    val vitalityTextPosition = defaultTextPosition().rememberDefault()

    @JvmField
    @field:Expose
    val experienceTextPosition = defaultTextPosition().rememberDefault()

    fun repairLoadedValues() {
        healthPosition.rememberDefault(defaultHealthPosition())
        manaPosition.rememberDefault(defaultManaPosition())
        vitalityPosition.updateDefault(oldVitalityPositionDefault, vitalityPositionDefault)
        experiencePosition.updateDefault(oldExperiencePositionDefault, experiencePositionDefault)
        defensePosition.rememberDefault(defaultDefensePosition())
        speedPosition.rememberDefault(defaultSpeedPosition())
        airPosition.rememberDefault(defaultAirPosition())
        vanillaHealthPosition.rememberDefault(defaultVanillaHealthPosition())
        vanillaExperiencePosition.rememberDefault(defaultVanillaExperiencePosition())
        vanillaAirPosition.rememberDefault(defaultVanillaAirPosition())
        healthTextPosition.rememberDefault(defaultTextPosition())
        manaTextPosition.rememberDefault(defaultTextPosition())
        vitalityTextPosition.rememberDefault(defaultTextPosition())
        experienceTextPosition.rememberDefault(defaultTextPosition())
    }
}

class CustomBarsSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Displays", desc = "Choose how each status is displayed.")
    @field:Accordion
    val displays = CustomBarsDisplayConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Numbers", desc = "Choose which separate numbers are shown.")
    @field:Accordion
    val numbers = CustomBarsNumberVisibilityConfig()
}

class CustomBarsDisplayConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Health", desc = "Choose the Health display.")
    @field:ConfigEditorDropdown
    var health = CustomBarDisplayMode.CUSTOM

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Mana", desc = "Choose the Mana display.")
    @field:ConfigEditorDropdown
    var mana = CustomBarDisplayMode.CUSTOM

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Vitality", desc = "Choose the Vitality display.")
    @field:ConfigEditorDropdown
    var vitality = CustomBarDisplayMode.CUSTOM

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Experience", desc = "Choose the Experience display.")
    @field:ConfigEditorDropdown
    var experience = CustomBarDisplayMode.CUSTOM

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Defense", desc = "Choose the Defense display.")
    @field:ConfigEditorDropdown
    var defense = CustomBarDisplayMode.CUSTOM

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Speed", desc = "Show the Speed readout.")
    @field:ConfigEditorBoolean
    var speed = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Air", desc = "Choose the Air display.")
    @field:ConfigEditorDropdown
    var air = CustomBarDisplayMode.CUSTOM
}

class CustomBarsNumberVisibilityConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Health", desc = "Show the Health number.")
    @field:ConfigEditorBoolean
    var health = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Mana", desc = "Show the Mana number.")
    @field:ConfigEditorBoolean
    var mana = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Vitality", desc = "Show the Vitality number.")
    @field:ConfigEditorBoolean
    var vitality = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Experience", desc = "Show the Experience number.")
    @field:ConfigEditorBoolean
    var experience = true
}

enum class CustomBarDisplayMode(private val displayName: String) {
    VANILLA("Vanilla"),
    CUSTOM("Custom"),
    HIDDEN("Hidden"),
    ;

    override fun toString(): String = displayName
}

class CustomBarsDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Resource Icon Position", desc = "Choose which side resource bar icons use.")
    @field:ConfigEditorDropdown
    var icons = CustomBarIconPosition.LEFT

    @JvmField
    @field:Expose
    @field:SerializedName(value = "textOutline", alternate = ["textShadow"])
    @field:ConfigOption(name = "Text Outline", desc = "Draw a vanilla-style outline around bar and readout text.")
    @field:ConfigEditorBoolean
    var textOutline = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Text Outline Color", desc = "Color used for text outlines.")
    @field:ConfigEditorColour
    val textOutlineColor: Property<ChromaColour> = Property.of(configColor(TEXT_OUTLINE_COLOR))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Health", desc = "Customize Health colors.")
    @field:Accordion
    val health = CustomResourceBarDetailsConfig(
        barDefault = configColor(HEALTH_COLOR),
        overflowDefault = configColor(HEALTH_OVERFLOW_COLOR),
        iconDefault = configColor(HEALTH_COLOR),
    )

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Mana", desc = "Customize Mana colors.")
    @field:Accordion
    val mana = CustomResourceBarDetailsConfig(
        barDefault = configColor(MANA_COLOR),
        overflowDefault = configColor(MANA_OVERFLOW_COLOR),
        iconDefault = configColor(MANA_COLOR),
    )

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Vitality", desc = "Customize Vitality colors.")
    @field:Accordion
    val vitality = CustomResourceBarDetailsConfig(
        barDefault = configColor(VITALITY_COLOR),
        overflowDefault = configColor(VITALITY_COLOR),
        iconDefault = configColor(VITALITY_COLOR),
    )

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Experience", desc = "Customize Experience colors.")
    @field:Accordion
    val experience = CustomProgressBarDetailsConfig(
        barDefault = configColor(EXPERIENCE_COLOR),
        textDefault = configColor(EXPERIENCE_COLOR),
    )

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Defense", desc = "Customize Defense colors.")
    @field:Accordion
    val defense = CustomReadoutDetailsConfig(iconDefault = configColor(DEFENSE_ICON_COLOR))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Speed", desc = "Customize Speed colors.")
    @field:Accordion
    val speed = CustomReadoutDetailsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Air", desc = "Customize Air colors.")
    @field:Accordion
    val air = CustomReadoutDetailsConfig()

    @JvmField
    @field:ConfigOption(name = "Reset Colors", desc = "Restore all Custom Bars colors.")
    @field:ConfigEditorButton(buttonText = "Reset")
    @field:ConfigOrder(1000)
    val resetColors = Runnable {
        val defaults = CustomBarsDetailsConfig()
        colorProperties().zip(defaults.colorProperties()).forEach { (current, default) ->
            current.set(default.get())
        }
    }

    private fun colorProperties(): List<Property<ChromaColour>> = buildList {
        add(textOutlineColor)
        addAll(health.colorProperties())
        addAll(mana.colorProperties())
        addAll(vitality.colorProperties())
        addAll(experience.colorProperties())
        addAll(defense.colorProperties())
        addAll(speed.colorProperties())
        addAll(air.colorProperties())
    }
}

open class CustomElementDetailsConfig(
    backgroundDefault: ChromaColour = configColor(TRACK_COLOR),
    textDefault: ChromaColour = configColor(TEXT_COLOR),
) {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Icon", desc = "Show this element's icon.")
    @field:ConfigEditorBoolean
    @field:ConfigOrder(0)
    var showIcon = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Background Color", desc = "Color used behind this element.")
    @field:ConfigEditorColour
    @field:ConfigOrder(30)
    val backgroundColor: Property<ChromaColour> = Property.of(backgroundDefault)

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Text Color", desc = "Color used for this element's text.")
    @field:ConfigEditorColour
    @field:ConfigOrder(40)
    val textColor: Property<ChromaColour> = Property.of(textDefault)

    internal open fun colorProperties(): List<Property<ChromaColour>> = listOf(backgroundColor, textColor)
}

class CustomResourceBarDetailsConfig(
    barDefault: ChromaColour = configColor(TEXT_COLOR),
    overflowDefault: ChromaColour = configColor(TEXT_COLOR),
    backgroundDefault: ChromaColour = configColor(TRACK_COLOR),
    textDefault: ChromaColour = barDefault,
    iconDefault: ChromaColour = barDefault,
) : CustomElementDetailsConfig(backgroundDefault, textDefault) {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Bar Color", desc = "Color used for the resource bar.")
    @field:ConfigEditorColour
    @field:ConfigOrder(10)
    val barColor: Property<ChromaColour> = Property.of(barDefault)

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Overflow Color", desc = "Color used for resources above their maximum.")
    @field:ConfigEditorColour
    @field:ConfigOrder(20)
    val overflowColor: Property<ChromaColour> = Property.of(overflowDefault)

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Icon Color", desc = "Color used for the resource icon.")
    @field:ConfigEditorColour
    @field:ConfigOrder(50)
    val iconColor: Property<ChromaColour> = Property.of(iconDefault)

    internal override fun colorProperties(): List<Property<ChromaColour>> =
        listOf(barColor, overflowColor, iconColor) + super.colorProperties()
}

class CustomProgressBarDetailsConfig(
    barDefault: ChromaColour = configColor(TEXT_COLOR),
    backgroundDefault: ChromaColour = configColor(TRACK_COLOR),
    textDefault: ChromaColour = barDefault,
) : CustomElementDetailsConfig(backgroundDefault, textDefault) {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Bar Color", desc = "Color used for the progress bar.")
    @field:ConfigEditorColour
    @field:ConfigOrder(10)
    val barColor: Property<ChromaColour> = Property.of(barDefault)

    internal override fun colorProperties(): List<Property<ChromaColour>> =
        listOf(barColor) + super.colorProperties()
}

class CustomReadoutDetailsConfig(
    backgroundDefault: ChromaColour = configColor(TRACK_COLOR),
    textDefault: ChromaColour = configColor(TEXT_COLOR),
    iconDefault: ChromaColour = configColor(TEXT_COLOR),
) : CustomElementDetailsConfig(backgroundDefault, textDefault) {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Icon Color", desc = "Color used for the readout icon.")
    @field:ConfigEditorColour
    @field:ConfigOrder(50)
    val iconColor: Property<ChromaColour> = Property.of(iconDefault)

    internal override fun colorProperties(): List<Property<ChromaColour>> =
        listOf(iconColor) + super.colorProperties()
}

enum class CustomBarIconPosition(private val displayName: String) {
    LEFT("Left"),
    RIGHT("Right"),
    ;

    override fun toString(): String = displayName
}

private fun configColor(argb: Int): ChromaColour {
    val color = Color(argb, true)
    return ChromaColour.fromRGB(color.red, color.green, color.blue, 0, color.alpha)
}

private const val TRACK_COLOR = 0xC0101010.toInt()
private const val HEALTH_COLOR = 0xFFFF5555.toInt()
private const val HEALTH_OVERFLOW_COLOR = 0xFFFFB42B.toInt()
private const val MANA_COLOR = 0xFF55FFFF.toInt()
private const val MANA_OVERFLOW_COLOR = 0xFFAA00FF.toInt()
private const val VITALITY_COLOR = 0xFFAA0000.toInt()
private const val EXPERIENCE_COLOR = 0xFF80FF20.toInt()
private const val DEFENSE_ICON_COLOR = 0xFF55FF55.toInt()
private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
private const val TEXT_OUTLINE_COLOR = 0xFF000000.toInt()

private val healthPositionDefault = HudPosition(-46, -35, centerX = true, centerY = false)
private val manaPositionDefault = HudPosition(47, -35, centerX = true, centerY = false)
private val vitalityPositionDefault = HudPosition(-64, -24, centerX = true, centerY = false)
private val experiencePositionDefault = HudPosition(29, -24, centerX = true, centerY = false)
private val defensePositionDefault = HudPosition(117, -34, centerX = true, centerY = false)
private val speedPositionDefault = HudPosition(117, -23, centerX = true, centerY = false)
private val airPositionDefault = HudPosition(117, -12, centerX = true, centerY = false)
private val oldVitalityPositionDefault = HudPosition(117, -45, centerX = true, centerY = false)
private val oldExperiencePositionDefault = HudPosition(0, -24, centerX = true, centerY = false)
private val vanillaHealthPositionDefault = HudPosition(-50, -30, centerX = true, centerY = false)
private val vanillaExperiencePositionDefault = HudPosition(0, -24, centerX = true, centerY = false)
private val vanillaAirPositionDefault = HudPosition(51, -40, centerX = true, centerY = false)

private fun defaultHealthPosition() = healthPositionDefault.copy()
private fun defaultManaPosition() = manaPositionDefault.copy()
private fun defaultVitalityPosition() = vitalityPositionDefault.copy()
private fun defaultExperiencePosition() = experiencePositionDefault.copy()
private fun defaultDefensePosition() = defensePositionDefault.copy()
private fun defaultSpeedPosition() = speedPositionDefault.copy()
private fun defaultAirPosition() = airPositionDefault.copy()
private fun defaultVanillaHealthPosition() = vanillaHealthPositionDefault.copy()
private fun defaultVanillaExperiencePosition() = vanillaExperiencePositionDefault.copy()
private fun defaultVanillaAirPosition() = vanillaAirPositionDefault.copy()
private fun defaultTextPosition() = HudPosition(centerY = false)

private fun HudPosition.copy() = HudPosition(x, y, scale, centerX, centerY)

private fun HudPosition.updateDefault(oldDefault: HudPosition, newDefault: HudPosition) {
    rememberDefault(oldDefault)
    val wasAtOldDefault = isAtDefault()
    rememberDefault(newDefault)
    if (wasAtOldDefault) resetToDefault()
}
