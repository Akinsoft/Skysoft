package com.skysoft.utils.gui

import net.minecraft.client.gui.GuiGraphicsExtractor

fun GuiGraphicsExtractor.fillOverlayBackground(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    color: Int,
    roundedCorners: Boolean,
) {
    if (!roundedCorners) {
        fill(left, top, right, bottom, color)
        return
    }
    fill(left + CORNER_RADIUS, top, right - CORNER_RADIUS, top + 1, color)
    fill(left + 1, top + 1, right - 1, top + CORNER_RADIUS, color)
    fill(left, top + CORNER_RADIUS, right, bottom - CORNER_RADIUS, color)
    fill(left + 1, bottom - CORNER_RADIUS, right - 1, bottom - 1, color)
    fill(left + CORNER_RADIUS, bottom - 1, right - CORNER_RADIUS, bottom, color)
}

private const val CORNER_RADIUS = 2
