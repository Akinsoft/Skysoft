package com.skysoft.utils.gui

import com.skysoft.utils.ColorUtilities.withScaledAlpha
import net.minecraft.client.gui.GuiGraphicsExtractor

object OverlayPanelStyle {
    const val BACKGROUND = 0xB0101010.toInt()
    const val OUTLINE = 0x80505050.toInt()
    const val PADDING = 5

    private const val SIDE_GAP = 4

    fun draw(context: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, opacity: Double = 1.0) {
        context.fill(x, y, x + width, y + height, BACKGROUND.withScaledAlpha(opacity))
        context.outline(x, y, width, height, OUTLINE.withScaledAlpha(opacity))
    }

    fun drawBeside(
        context: GuiGraphicsExtractor,
        trackerWidth: Int,
        placeRight: Boolean,
        width: Int,
        height: Int,
        opacity: Double,
    ): Rect {
        val x = if (placeRight) trackerWidth + SIDE_GAP else -width - SIDE_GAP
        val bounds = Rect(x, 0, width, height)
        draw(context, bounds.x, bounds.y, bounds.width, bounds.height, opacity)
        return bounds
    }
}
