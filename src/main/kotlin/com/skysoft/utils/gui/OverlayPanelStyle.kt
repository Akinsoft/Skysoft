package com.skysoft.utils.gui

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.ColorUtilities.COLOR_CHANNEL_MAX
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.ColorUtilities.withAlpha
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import kotlin.math.min
import net.minecraft.client.gui.GuiGraphicsExtractor

object OverlayPanelStyle {
    const val BACKGROUND = 0xB0101010.toInt()
    const val OUTLINE = 0x80505050.toInt()
    const val PADDING = 5

    val hudBackgroundColor: Int
        get() = SkysoftConfigGui.config().settings.hudAppearance.backgroundColor.get().toColor().rgb

    private const val SIDE_GAP = 4
    private const val PERCENT_MAX = 100

    fun draw(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        opacity: Double = 1.0,
        backgroundColor: Int = BACKGROUND,
    ) {
        context.fill(x, y, x + width, y + height, backgroundColor.withScaledAlpha(opacity))
        context.outline(x, y, width, height, OUTLINE.withScaledAlpha(opacity))
    }

    fun draw(context: GuiGraphicsExtractor, bounds: Rect, opacity: Double = 1.0) {
        draw(context, bounds.x, bounds.y, bounds.width, bounds.height, opacity)
    }

    fun fadedHudBackgroundColor(alpha: Int, opacityPercent: Int): Int =
        hudBackgroundColor.withAlpha(min(alpha, opacityPercent * COLOR_CHANNEL_MAX / PERCENT_MAX))

    fun drawHudBackground(context: GuiGraphicsExtractor, width: Int, height: Int) {
        draw(context, 0, 0, width, height, backgroundColor = hudBackgroundColor)
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
        return Rect(x, 0, width, height).also { draw(context, it, opacity) }
    }
}
