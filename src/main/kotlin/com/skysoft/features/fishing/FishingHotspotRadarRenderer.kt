package com.skysoft.features.fishing

import com.skysoft.utils.WorldVec
import com.skysoft.utils.render.BlockHighlightRenderer
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldLabelRenderer
import com.skysoft.utils.render.WorldLabelStyle
import com.skysoft.utils.toWorldVec
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import java.awt.Color

internal object FishingHotspotRadarRenderer {
    fun renderWorld(
        context: SkysoftRenderContext,
        guess: WorldVec,
        drawCrosshairLine: Boolean,
    ) {
        val blockCenter = guess.blockCenter()
        BlockHighlightRenderer.drawBlock(
            context,
            guess,
            GUESS_OUTLINE_COLOR,
            GUESS_FILL_COLOR,
            GUESS_LINE_WIDTH,
        )
        WorldLabelRenderer.draw(
            context,
            guess + LABEL_OFFSET,
            labelLines(context, guess),
            LABEL_STYLE,
        )
        if (drawCrosshairLine) {
            context.drawLineToCrosshair(blockCenter, GUESS_OUTLINE_COLOR, GUESS_LINE_WIDTH)
        }
    }

    private fun labelLines(context: SkysoftRenderContext, location: WorldVec): List<Component> {
        val distance = location.blockCenter().distance(context.camera.position().toWorldVec()).toInt()
        return listOf(
            Component.literal("HOTSPOT GUESS").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
            Component.literal("${distance}m").withStyle(ChatFormatting.YELLOW),
        )
    }

    private const val GUESS_LINE_WIDTH = 2
    private val GUESS_OUTLINE_COLOR = Color(255, 85, 255, 210)
    private val GUESS_FILL_COLOR = Color(170, 0, 255, 45)
    private val LABEL_OFFSET = WorldVec(0.5, 1.8, 0.5)
    private val LABEL_STYLE = WorldLabelStyle()
}
