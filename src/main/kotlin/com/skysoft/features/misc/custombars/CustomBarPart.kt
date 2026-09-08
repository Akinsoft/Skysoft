package com.skysoft.features.misc.custombars

import com.skysoft.config.CustomBarDisplayMode
import com.skysoft.config.CustomBarIconPosition
import com.skysoft.config.CustomElementDetailsConfig
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.core.HudDimensions
import com.skysoft.config.core.HudPosition
import com.skysoft.data.SkyBlockIsland
import com.skysoft.features.inventory.InventoryHudLayout
import com.skysoft.features.misc.custombars.VanillaCustomBarDisplay.Companion.HOTBAR_WIDTH
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft

internal enum class CustomBarPart(val label: String) {
    HEALTH("Health Bar"),
    MANA("Mana Bar"),
    VITALITY("Vitality Bar"),
    EXPERIENCE("Experience Bar"),
    DEFENSE("Defense"),
    SPEED("Speed"),
    AIR("Air"),
    ;

    val defaultWidth: Int
        get() = when (this) {
            HEALTH, MANA -> (resourceRowWidth() - BAR_GAP) / 2
            VITALITY -> vitalityResourceWidth()
            EXPERIENCE -> resourceRowWidth() - BAR_GAP - vitalityResourceWidth()
            DEFENSE, SPEED, AIR -> READOUT_WIDTH
        }

    val dimensions: HudDimensions?
        get() = when (this) {
            HEALTH -> config.healthDimensions
            MANA -> config.manaDimensions
            VITALITY -> config.vitalityDimensions
            EXPERIENCE -> config.experienceDimensions
            DEFENSE, SPEED, AIR -> null
        }

    val width: Int
        get() = if (usesVanillaDisplay()) vanillaDisplay().width else dimensions?.width(defaultWidth, MIN_RESOURCE_WIDTH)
            ?: defaultWidth
    val height: Int
        get() = if (usesVanillaDisplay()) vanillaDisplay().height else dimensions?.height(
            RESOURCE_HEIGHT,
            MIN_RESOURCE_HEIGHT,
        ) ?: READOUT_ELEMENT_HEIGHT
    val isResource: Boolean get() = dimensions != null
    val iconSlotWidth: Int
        get() = if (!usesVanillaDisplay() && visualDetails.showIcon) ICON_SLOT_WIDTH else 0
    val trackX: Int get() = if (config.details.icons == CustomBarIconPosition.LEFT) iconSlotWidth else 0
    val trackWidth: Int get() = width - iconSlotWidth
    val iconX: Int
        get() = if (config.details.icons == CustomBarIconPosition.RIGHT) width - ICON_SLOT_WIDTH else 0
    val visualDetails: CustomElementDetailsConfig
        get() = when (this) {
            HEALTH -> config.details.health
            MANA -> config.details.mana
            VITALITY -> config.details.vitality
            EXPERIENCE -> config.details.experience
            DEFENSE -> config.details.defense
            SPEED -> config.details.speed
            AIR -> config.details.air
        }

    fun isCustomVisible(): Boolean = isAvailable && when (this) {
        HEALTH -> config.settings.displays.health == CustomBarDisplayMode.CUSTOM
        MANA -> config.settings.displays.mana == CustomBarDisplayMode.CUSTOM
        VITALITY -> config.settings.displays.vitality == CustomBarDisplayMode.CUSTOM
        EXPERIENCE -> config.settings.displays.experience == CustomBarDisplayMode.CUSTOM
        DEFENSE -> config.settings.displays.defense == CustomBarDisplayMode.CUSTOM
        SPEED -> config.settings.displays.speed
        AIR ->
            config.settings.displays.air == CustomBarDisplayMode.CUSTOM &&
                Minecraft.getInstance().player?.isUnderWater == true
    }

    fun isEditorVisible(): Boolean = isCustomVisible() || (usesVanillaDisplay() && vanillaDisplay().isVisible())

    fun usesVanillaDisplay(): Boolean = when (this) {
        HEALTH -> config.settings.displays.health == CustomBarDisplayMode.VANILLA
        EXPERIENCE -> config.settings.displays.experience == CustomBarDisplayMode.VANILLA
        AIR -> config.settings.displays.air == CustomBarDisplayMode.VANILLA
        MANA, VITALITY, DEFENSE, SPEED -> false
    }

    fun isNumberVisible(): Boolean = isAvailable && when (this) {
        HEALTH -> config.settings.numbers.health
        MANA -> config.settings.numbers.mana
        VITALITY -> config.settings.numbers.vitality
        EXPERIENCE -> config.settings.numbers.experience
        DEFENSE, SPEED, AIR -> error("$label does not have separate number text")
    }

    private val isAvailable: Boolean get() = !inRift || this != VITALITY

    fun position(): HudPosition = if (usesVanillaDisplay()) {
        when (this) {
            HEALTH -> config.vanillaHealthPosition
            EXPERIENCE -> config.vanillaExperiencePosition
            AIR -> config.vanillaAirPosition
            MANA, VITALITY, DEFENSE, SPEED -> error("$label has no vanilla position")
        }
    } else {
        when (this) {
            HEALTH -> config.healthPosition
            MANA -> config.manaPosition
            VITALITY -> config.vitalityPosition
            EXPERIENCE -> config.experiencePosition
            DEFENSE -> config.defensePosition
            SPEED -> config.speedPosition
            AIR -> config.airPosition
        }
    }

    fun textPosition(): HudPosition = when (this) {
        HEALTH -> config.healthTextPosition
        MANA -> config.manaTextPosition
        VITALITY -> config.vitalityTextPosition
        EXPERIENCE -> config.experienceTextPosition
        DEFENSE, SPEED, AIR -> error("$label does not have movable bar text")
    }

    fun editorDimensions(): HudDimensions? = dimensions.takeUnless { usesVanillaDisplay() }

    fun vanillaDisplay(): VanillaCustomBarDisplay = when (this) {
        HEALTH -> VanillaCustomBarDisplay.HEALTH
        EXPERIENCE -> VanillaCustomBarDisplay.EXPERIENCE
        AIR -> VanillaCustomBarDisplay.AIR
        MANA, VITALITY, DEFENSE, SPEED -> error("$label has no vanilla display")
    }

    fun resize(width: Int, height: Int) {
        editorDimensions()?.resize(width, height, defaultWidth, RESOURCE_HEIGHT)
    }

    fun layoutOffsetX(): Int {
        if (usesVanillaDisplay() || !usesInventoryHudWidth()) return 0
        val oldWidth = when (this) {
            HEALTH, MANA -> HALF_RESOURCE_WIDTH
            VITALITY -> VITALITY_RESOURCE_WIDTH
            EXPERIENCE -> EXPERIENCE_RESOURCE_WIDTH
            DEFENSE, SPEED, AIR -> return 0
        }
        val widthChange = halfRoundedUp(width) - halfRoundedUp(oldWidth)
        val rowInset = (HOTBAR_WIDTH - resourceRowWidth()) / 2
        return when (this) {
            HEALTH, VITALITY -> rowInset + widthChange
            MANA, EXPERIENCE -> -rowInset - widthChange
            DEFENSE, SPEED, AIR -> 0
        }
    }

    companion object {
        private const val BAR_GAP = 4
        private const val ICON_SLOT_WIDTH = 10
        private const val HALF_BAR_WIDTH = (HOTBAR_WIDTH - BAR_GAP - ICON_SLOT_WIDTH * 2) / 2
        private const val HALF_RESOURCE_WIDTH = ICON_SLOT_WIDTH + HALF_BAR_WIDTH
        private const val VITALITY_RESOURCE_WIDTH = 54
        private const val EXPERIENCE_RESOURCE_WIDTH = HOTBAR_WIDTH - BAR_GAP - VITALITY_RESOURCE_WIDTH
        private const val RESOURCE_HEIGHT = 14
        const val RESOURCE_BAR_Y = 5
        const val RESOURCE_BOTTOM_PADDING = 2
        const val MIN_TRACK_HEIGHT = 3
        const val MIN_RESOURCE_WIDTH = 24
        const val MIN_RESOURCE_HEIGHT = RESOURCE_BAR_Y + RESOURCE_BOTTOM_PADDING + MIN_TRACK_HEIGHT
        const val READOUT_WIDTH = 44
        private const val READOUT_ELEMENT_HEIGHT = 11

        private fun usesInventoryHudWidth(): Boolean = SkysoftConfigGui.config().gui.inventoryHud.enabled

        private fun resourceRowWidth(): Int =
            if (usesInventoryHudWidth()) InventoryHudLayout.MAIN_PANEL_WIDTH else HOTBAR_WIDTH

        private fun vitalityResourceWidth(): Int =
            (resourceRowWidth() * VITALITY_RESOURCE_WIDTH.toFloat() / HOTBAR_WIDTH).roundToInt()

        private fun halfRoundedUp(value: Int): Int = (value + 1) / 2
    }
}

private val config get() = SkysoftConfigGui.config().gui.customBars
private val inRift get() = SkyBlockIsland.THE_RIFT.isInIsland()
