package com.skysoft.features.misc.custombars

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.core.HudPosition
import com.skysoft.features.misc.AbsorptionHeartLayout
import com.skysoft.features.misc.SkyBlockLevelBar
import com.skysoft.gui.transform
import kotlin.math.ceil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.player.Player

internal enum class VanillaCustomBarDisplay {
    HEALTH,
    EXPERIENCE,
    AIR,
    ;

    val width: Int
        get() = when (this) {
            HEALTH, AIR -> VANILLA_STATUS_WIDTH
            EXPERIENCE -> HOTBAR_WIDTH
        }

    val height: Int
        get() = when (this) {
            HEALTH -> vanillaHealthHeight()
            AIR -> ICON_SIZE
            EXPERIENCE -> VANILLA_EXPERIENCE_HEIGHT
        }

    fun isVisible(): Boolean {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return false
        val gameMode = minecraft.gameMode ?: return false
        return when (this) {
            HEALTH -> gameMode.canHurtPlayer() && minecraft.cameraEntity is Player
            AIR -> gameMode.canHurtPlayer() && (minecraft.cameraEntity as? Player)?.let {
                it.isEyeInFluid(FluidTags.WATER) || it.airSupply < it.maxAirSupply
            } == true
            EXPERIENCE -> gameMode.hasExperience() && (
                (
                    !SkysoftConfigGui.config().gui.customBars.settings.numbers.experience &&
                        SkyBlockLevelBar.experienceLevelVisibility(player.experienceLevel) > 0
                    ) || isExperienceBarVisible()
                )
        }
    }

    fun renderPositioned(
        context: GuiGraphicsExtractor,
        position: HudPosition,
        render: () -> Unit,
    ) {
        val displayHeight = height
        val transform = position.transform(width, displayHeight)
        val sourceX: Int
        val sourceY: Int
        when (this) {
            HEALTH -> {
                sourceX = context.guiWidth() / 2 - VANILLA_HUD_HALF_WIDTH
                sourceY = context.guiHeight() - VANILLA_HEALTH_TOP_OFFSET - (displayHeight - ICON_SIZE)
            }
            EXPERIENCE -> {
                sourceX = (context.guiWidth() - HOTBAR_WIDTH) / 2
                sourceY = context.guiHeight() - VANILLA_EXPERIENCE_TOP_OFFSET
            }
            AIR -> {
                sourceX = context.guiWidth() / 2 + VANILLA_AIR_LEFT_OFFSET
                sourceY = context.guiHeight() - VANILLA_AIR_TOP_OFFSET
            }
        }
        transform.render(context) {
            pose().translate(-sourceX.toFloat(), -sourceY.toFloat())
            render()
        }
    }

    private fun isExperienceBarVisible(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val vehicle = player.jumpableVehicle()
        if (!player.connection.waypointManager.hasWaypoints()) return vehicle == null
        val prioritizesJump = vehicle != null && (player.jumpRidingScale > 0f || vehicle.jumpCooldown > 0)
        return !prioritizesJump && player.experienceDisplayStartTick + EXPERIENCE_DISPLAY_TICKS > player.tickCount
    }

    private fun vanillaHealthHeight(): Int {
        val player = Minecraft.getInstance().player ?: return 0
        val absorption = ceil(player.absorptionAmount.toDouble()).toInt()
        val currentHealth = ceil(player.health.toDouble()).toInt()
        val maximumHealth = AbsorptionHeartLayout.resolveMaximumHealth(maxOf(player.maxHealth, currentHealth.toFloat()), player)
        val healthContainers = ceil(maximumHealth / 2.0).toInt()
        val totalContainers = healthContainers + (absorption + 1) / 2
        val vanillaRowCount = ceil((maximumHealth + absorption) / VANILLA_HEALTH_POINTS_PER_ROW)
            .toInt()
            .coerceAtLeast(1)
        val rowHeight = maxOf(VANILLA_HEART_ROW_HEIGHT - (vanillaRowCount - 2), VANILLA_MIN_HEART_ROW_HEIGHT)
        val rowCount = ((totalContainers + VANILLA_STATUS_ICON_COUNT - 1) / VANILLA_STATUS_ICON_COUNT).coerceAtLeast(1)
        return ICON_SIZE + (rowCount - 1) * rowHeight
    }

    companion object {
        const val HOTBAR_WIDTH = 182
        const val ICON_SIZE = 9
        val AIR_SPRITE = Identifier.withDefaultNamespace("hud/air")
    }
}

private const val VANILLA_HUD_HALF_WIDTH = VanillaCustomBarDisplay.HOTBAR_WIDTH / 2
private const val VANILLA_STATUS_ICON_COUNT = 10
private const val VANILLA_STATUS_WIDTH = 82
private const val VANILLA_HEALTH_POINTS_PER_ROW = 20f
private const val VANILLA_HEART_ROW_HEIGHT = 10
private const val VANILLA_MIN_HEART_ROW_HEIGHT = 3
private const val VANILLA_HEALTH_TOP_OFFSET = 39
private const val VANILLA_AIR_LEFT_OFFSET = 10
private const val VANILLA_AIR_TOP_OFFSET = 49
private const val VANILLA_EXPERIENCE_HEIGHT = 11
private const val VANILLA_EXPERIENCE_TOP_OFFSET = 35
private const val EXPERIENCE_DISPLAY_TICKS = 100
