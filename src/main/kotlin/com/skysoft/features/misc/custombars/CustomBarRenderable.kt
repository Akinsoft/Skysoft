package com.skysoft.features.misc.custombars

import com.skysoft.config.CustomReadoutDetailsConfig
import com.skysoft.config.CustomResourceBarDetailsConfig
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.skyblock.SkyBlockStatGlyph
import com.skysoft.features.misc.SkyBlockLevelBar
import com.skysoft.features.misc.custombars.CustomBarPart.Companion.MIN_TRACK_HEIGHT
import com.skysoft.features.misc.custombars.CustomBarPart.Companion.READOUT_WIDTH
import com.skysoft.features.misc.custombars.CustomBarPart.Companion.RESOURCE_BAR_Y
import com.skysoft.features.misc.custombars.CustomBarPart.Companion.RESOURCE_BOTTOM_PADDING
import com.skysoft.features.misc.custombars.VanillaCustomBarDisplay.Companion.AIR_SPRITE
import com.skysoft.features.misc.custombars.VanillaCustomBarDisplay.Companion.ICON_SIZE
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.render.drawGlossyProgressBar
import com.skysoft.utils.render.fillGlossyRect
import com.skysoft.utils.render.fillGlossyRoundedRect
import com.skysoft.utils.render.fillRoundedRect
import com.skysoft.utils.render.roundedRectVerticalInset
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.util.ARGB
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal class CustomBarRenderable private constructor(
    private val part: CustomBarPart,
    private val health: BarValue?,
    private val mana: BarValue?,
    private val vitality: BarValue?,
    private val defense: Int?,
) : GuiRenderable {
    override val width: Int = part.width
    override val height: Int = part.height

    override fun render(context: GuiGraphicsExtractor) {
        val player = Minecraft.getInstance().player
        when (part) {
            CustomBarPart.HEALTH -> drawBar(
                context,
                health,
                config.details.health,
                if (inRift) SkyBlockStatGlyph.HEARTS.toString() else SkyBlockStatGlyph.HEALTH.toString(),
            )
            CustomBarPart.MANA -> drawBar(
                context,
                mana,
                config.details.mana,
                SkyBlockStatGlyph.INTELLIGENCE.toString(),
            )
            CustomBarPart.VITALITY -> drawBar(
                context,
                vitality,
                config.details.vitality,
                SkyBlockStatGlyph.VITALITY.toString(),
            )
            CustomBarPart.EXPERIENCE -> {
                val details = config.details.experience
                drawExperienceIcon(context, part.iconX, resourceBarHeight())
                context.drawGlossyProgressBar(
                    part.trackX,
                    RESOURCE_BAR_Y,
                    part.trackWidth,
                    resourceBarHeight(),
                    SkyBlockLevelBar.displayedExperienceProgress(player?.experienceProgress ?: 0f),
                    SkyBlockLevelBar.displayedExperienceBarColor(details.barColor.get().toColor().rgb),
                    details.backgroundColor.get().toColor().rgb,
                )
            }
            CustomBarPart.DEFENSE -> drawReadout(
                context,
                if (inRift) SkyBlockStatGlyph.RIFT_DAMAGE.toString() else SkyBlockStatGlyph.DEFENSE.toString(),
                defense?.addSeparators() ?: "---",
                config.details.defense,
            )
            CustomBarPart.SPEED -> drawReadout(
                context,
                SkyBlockStatGlyph.SPEED.toString(),
                player?.skyBlockSpeed()?.addSeparators() ?: "---",
                config.details.speed,
            )
            CustomBarPart.AIR -> {
                val remainingTicks = player?.airSupply ?: 0
                drawAirReadout(
                    context,
                    remainingTicks.coerceAtLeast(0) / TICKS_PER_SECOND,
                    config.details.air,
                )
            }
        }
    }

    private fun drawBar(
        context: GuiGraphicsExtractor,
        value: BarValue?,
        details: CustomResourceBarDetailsConfig,
        icon: String,
    ) {
        if (part.iconSlotWidth > 0) {
            val iconY = RESOURCE_BAR_Y + (resourceBarHeight() - Minecraft.getInstance().font.lineHeight) / 2
            drawCustomBarText(context, icon, part.iconX, iconY, details.iconColor.get().toColor().rgb)
        }
        drawResourceBar(
            context,
            part.trackX,
            RESOURCE_BAR_Y,
            part.trackWidth,
            resourceBarHeight(),
            value,
            details.barColor.get().toColor().rgb,
            details.overflowColor.get().toColor().rgb,
            details.backgroundColor.get().toColor().rgb,
        )
    }

    private fun drawResourceBar(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        value: BarValue?,
        color: Int,
        overflowColor: Int,
        backgroundColor: Int,
    ) {
        context.fillRoundedRect(x, y, width, height, backgroundColor)
        if (value == null) return
        val innerWidth = width - INNER_PADDING * 2
        val capacity = value.maximum + value.displayOverflow
        val totalWidth = (innerWidth * value.displayedCurrent.toFloat() / capacity.coerceAtLeast(1))
            .roundToInt()
            .coerceIn(0, innerWidth)
        if (totalWidth == 0) return
        val baseWidth = (totalWidth * value.regularCurrent.toFloat() / value.displayedCurrent.coerceAtLeast(1))
            .roundToInt()
            .coerceIn(0, totalWidth)
        val overflowWidth = totalWidth - baseWidth
        val fillX = x + INNER_PADDING
        val fillY = y + INNER_PADDING
        val fillHeight = height - INNER_PADDING * 2
        if (baseWidth > 0) context.fillGlossyRoundedRect(fillX, fillY, baseWidth, fillHeight, color)
        if (overflowWidth > 0) {
            context.fillGlossyRoundedRect(fillX + baseWidth, fillY, overflowWidth, fillHeight, overflowColor)
        }
        if (baseWidth > 0 && overflowWidth > 0) {
            val baseTransitionWidth = OVERFLOW_TRANSITION_HALF_WIDTH.coerceAtMost(baseWidth)
            val overflowTransitionWidth = OVERFLOW_TRANSITION_HALF_WIDTH.coerceAtMost(overflowWidth)
            val transitionWidth = baseTransitionWidth + overflowTransitionWidth
            val transitionX = fillX + baseWidth - baseTransitionWidth
            repeat(transitionWidth) { offset ->
                val colorProgress = offset.toFloat() / (transitionWidth - 1)
                val barOffset = baseWidth - baseTransitionWidth + offset
                val verticalInset = roundedRectVerticalInset(barOffset, totalWidth, fillHeight)
                context.fillGlossyRect(
                    transitionX + offset,
                    fillY + verticalInset,
                    1,
                    fillHeight - verticalInset * 2,
                    ARGB.srgbLerp(colorProgress, color, overflowColor),
                )
            }
        }
    }

    private fun drawExperienceIcon(context: GuiGraphicsExtractor, x: Int, barHeight: Int) {
        if (part.iconSlotWidth == 0) return
        if (inRift && !SkyBlockLevelBar.isReplacingExperience) {
            val y = RESOURCE_BAR_Y + (barHeight - Minecraft.getInstance().font.lineHeight) / 2
            drawCustomBarText(
                context,
                SkyBlockStatGlyph.RIFT_TIME.toString(),
                x,
                y,
                config.details.experience.barColor.get().toColor().rgb,
            )
        } else {
            val y = RESOURCE_BAR_Y + (barHeight - EXPERIENCE_ICON_SIZE) / 2
            EXPERIENCE_ICON.renderAt(context, x + EXPERIENCE_ICON_X, y)
        }
    }

    private fun resourceBarHeight(): Int =
        (height - RESOURCE_BAR_Y - RESOURCE_BOTTOM_PADDING).coerceAtLeast(MIN_TRACK_HEIGHT)

    private fun drawReadout(
        context: GuiGraphicsExtractor,
        icon: String,
        value: String,
        details: CustomReadoutDetailsConfig,
    ) {
        val font = Minecraft.getInstance().font
        val iconWidth = if (details.showIcon) font.width(icon) + READOUT_CONTENT_GAP else 0
        val contentWidth = iconWidth + font.width(value)
        val contentX = centeredReadoutX(contentWidth)
        context.fillRoundedRect(
            0,
            READOUT_BACKGROUND_Y,
            READOUT_WIDTH,
            READOUT_HEIGHT,
            details.backgroundColor.get().toColor().rgb,
        )
        if (details.showIcon) drawCustomBarText(context, icon, contentX, READOUT_CONTENT_Y, details.iconColor.get().toColor().rgb)
        drawCustomBarText(
            context,
            value,
            contentX + iconWidth,
            READOUT_CONTENT_Y,
            details.textColor.get().toColor().rgb,
        )
    }

    private fun drawAirReadout(
        context: GuiGraphicsExtractor,
        seconds: Int,
        details: CustomReadoutDetailsConfig,
    ) {
        val text = "${seconds}s"
        val font = Minecraft.getInstance().font
        val iconWidth = if (details.showIcon) ICON_SIZE + READOUT_CONTENT_GAP else 0
        val contentWidth = iconWidth + font.width(text)
        val contentX = centeredReadoutX(contentWidth)
        context.fillRoundedRect(
            0,
            READOUT_BACKGROUND_Y,
            READOUT_WIDTH,
            READOUT_HEIGHT,
            details.backgroundColor.get().toColor().rgb,
        )
        if (details.showIcon) {
            context.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                AIR_SPRITE,
                contentX,
                READOUT_CONTENT_Y,
                ICON_SIZE,
                ICON_SIZE,
                details.iconColor.get().toColor().rgb,
            )
        }
        drawCustomBarText(
            context,
            text,
            contentX + iconWidth,
            READOUT_CONTENT_Y,
            details.textColor.get().toColor().rgb,
        )
    }

    private fun centeredReadoutX(contentWidth: Int): Int =
        ((READOUT_WIDTH - contentWidth) / 2f).roundToInt()

    companion object {
        fun create(part: CustomBarPart): GuiRenderable =
            CustomBarRenderable(
                part,
                CustomBarState.displayedHealth(),
                CustomBarState.mana,
                CustomBarState.vitality,
                CustomBarState.displayedDefense(),
            )
    }
}

private val config get() = SkysoftConfigGui.config().gui.customBars
private val inRift get() = SkyBlockIsland.THE_RIFT.isInIsland()

private fun net.minecraft.world.entity.player.Player.skyBlockSpeed(): Int =
    ((if (isSprinting) speed / SPRINT_SPEED_MULTIPLIER else speed) * SPEED_SCALE).roundToInt()

private const val READOUT_HEIGHT = 9
private const val READOUT_BACKGROUND_Y = 1
private const val READOUT_CONTENT_Y = 2
private const val READOUT_CONTENT_GAP = 1
private const val INNER_PADDING = 1
private const val EXPERIENCE_ICON_SIZE = 11
private const val EXPERIENCE_ICON_X = -2
private const val OVERFLOW_TRANSITION_HALF_WIDTH = 4
private const val TICKS_PER_SECOND = 20
private const val SPEED_SCALE = 1_000f
private const val SPRINT_SPEED_MULTIPLIER = 1.3f
private val EXPERIENCE_ICON = ItemIconRenderable(ItemStack(Items.EXPERIENCE_BOTTLE), EXPERIENCE_ICON_SIZE / 16.0)
