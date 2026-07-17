package com.skysoft.utils.gui

import net.minecraft.client.gui.GuiGraphicsExtractor

object OverlayPanelStyle {
    const val BACKGROUND = 0xB0101010.toInt()
    const val OUTLINE = 0x80505050.toInt()
    const val PADDING = 5

    fun draw(context: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        context.fill(x, y, x + width, y + height, BACKGROUND)
        context.outline(x, y, width, height, OUTLINE)
    }
}
