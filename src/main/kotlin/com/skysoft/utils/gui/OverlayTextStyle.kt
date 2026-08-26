package com.skysoft.utils.gui

import com.skysoft.utils.ColorUtilities.withScaledAlpha
import net.minecraft.client.gui.GuiGraphicsExtractor

object OverlayTextStyle {
    const val TITLE_HEIGHT = 12
    const val ROW_HEIGHT = 11
    private const val CONTROL_HOVER_COLOR = 0x20FFFFFF

    fun drawControlHover(context: GuiGraphicsExtractor, bounds: Rect, opacity: Double) {
        context.fill(
            bounds.x,
            bounds.y,
            bounds.x + bounds.width,
            bounds.y + bounds.height,
            CONTROL_HOVER_COLOR.withScaledAlpha(opacity),
        )
    }
}
