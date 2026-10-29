package com.skysoft.utils.gui

import com.skysoft.utils.render.fillRoundedBounds
import net.minecraft.client.gui.GuiGraphicsExtractor

fun GuiGraphicsExtractor.fillOverlayBackground(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    color: Int,
    roundedCorners: Boolean,
) {
    if (roundedCorners) fillRoundedBounds(left, top, right, bottom, color) else fill(left, top, right, bottom, color)
}
