package com.skysoft.utils.gui

import com.skysoft.utils.ColorUtilities.withScaledAlpha
import net.minecraft.client.gui.GuiGraphicsExtractor

object OverlayTextStyle {
    const val TITLE_HEIGHT = 12
    const val ROW_HEIGHT = 11
    private const val CONTROL_HOVER_COLOR = 0x20FFFFFF

    fun title(text: String): String = "§e§l$text"

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

object OverlayItemRowStyle {
    const val HEIGHT = 13
    const val TEXT_Y_OFFSET = 2
    const val ICON_SCALE = 0.75
    const val ICON_TEXT_OFFSET = 14
    const val VALUE_COLUMN_GAP = 8
    const val QUANTITY_COLUMN_GAP = 4
}
