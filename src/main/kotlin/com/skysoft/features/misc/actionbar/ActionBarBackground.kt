package com.skysoft.features.misc.actionbar

import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.ColorUtilities.ARGB_ALPHA_SHIFT
import com.skysoft.utils.ColorUtilities.COLOR_CHANNEL_MAX
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.OverlayMessages
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.min

object ActionBarBackground {
    private const val BACKGROUND_RGB = 0x101010
    private const val X_PADDING = 4
    private const val Y_PADDING = 3
    private const val CORNER_RADIUS = 2
    private const val PERCENT_MAX = 100
    private const val FADE_TICKS = 20.0f
    private const val TEXT_Y_FROM_BOTTOM = 72
    private const val FONT_HEIGHT = 9

    fun register() {
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.OVERLAY_MESSAGE,
            SkysoftMod.id("action_bar_background"),
            ActionBarBackground::render,
        )
    }

    private fun render(context: GuiGraphicsExtractor, tick: DeltaTracker) {
        val minecraft = Minecraft.getInstance()
        val config = SkysoftConfigGui.config().gui.actionBar
        if (!config.background || MinecraftClient.isGuiHidden(minecraft)) {
            return
        }

        val message = OverlayMessages.message(minecraft)
        val time = OverlayMessages.time(minecraft)
        if (message == null || time <= 0) {
            return
        }

        val textWidth = minecraft.font.width(message)
        if (message.string.isBlank() || textWidth <= 0) {
            return
        }

        val alpha = ((time - tick.getGameTimeDeltaPartialTick(false)) * COLOR_CHANNEL_MAX / FADE_TICKS).toInt()
        if (alpha <= 0) {
            return
        }

        val x = (context.guiWidth() - textWidth) / 2
        val textY = context.guiHeight() - TEXT_Y_FROM_BOTTOM
        val maxAlpha = config.backgroundOpacity * COLOR_CHANNEL_MAX / PERCENT_MAX
        val color = (min(maxAlpha, alpha) shl ARGB_ALPHA_SHIFT) or BACKGROUND_RGB
        val left = x - X_PADDING
        val top = textY - Y_PADDING
        val right = x + textWidth + X_PADDING
        val bottom = textY + FONT_HEIGHT + Y_PADDING
        context.nextStratum()
        if (config.roundedCorners) {
            context.fill(left + CORNER_RADIUS, top, right - CORNER_RADIUS, top + 1, color)
            context.fill(left + 1, top + 1, right - 1, top + CORNER_RADIUS, color)
            context.fill(left, top + CORNER_RADIUS, right, bottom - CORNER_RADIUS, color)
            context.fill(left + 1, bottom - CORNER_RADIUS, right - 1, bottom - 1, color)
            context.fill(left + CORNER_RADIUS, bottom - 1, right - CORNER_RADIUS, bottom, color)
        } else {
            context.fill(left, top, right, bottom, color)
        }
    }
}
