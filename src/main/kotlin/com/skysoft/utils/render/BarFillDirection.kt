package com.skysoft.utils.render

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.ARGB
import kotlin.math.roundToInt

internal enum class BarFillDirection {
    RIGHT,
    UP,
}

internal fun GuiGraphicsExtractor.drawGlossyProgressBar(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    progress: Float,
    color: Int,
    backgroundColor: Int,
    direction: BarFillDirection = BarFillDirection.RIGHT,
) {
    fillRoundedRect(x, y, width, height, backgroundColor)
    val innerWidth = width - INNER_PADDING * 2
    val innerHeight = height - INNER_PADDING * 2
    when (direction) {
        BarFillDirection.RIGHT -> {
            val fillWidth = (innerWidth * progress.coerceIn(0f, 1f)).roundToInt()
            if (fillWidth > 0) fillGlossyRoundedRect(x + INNER_PADDING, y + INNER_PADDING, fillWidth, innerHeight, color)
        }

        BarFillDirection.UP -> {
            val fillHeight = (innerHeight * progress.coerceIn(0f, 1f)).roundToInt()
            if (fillHeight > 0) {
                fillGlossyRoundedRect(
                    x + INNER_PADDING,
                    y + height - INNER_PADDING - fillHeight,
                    innerWidth,
                    fillHeight,
                    color,
                )
            }
        }
    }
}

internal fun GuiGraphicsExtractor.fillRoundedRect(x: Int, y: Int, width: Int, height: Int, color: Int) {
    if (height <= CORNER_RADIUS * 2) {
        fill(x, y, x + width, y + height, color)
        return
    }
    if (width <= CORNER_RADIUS * 2) {
        repeat(width) { offset ->
            val verticalInset = roundedRectVerticalInset(offset, width, height)
            fill(x + offset, y + verticalInset, x + offset + 1, y + height - verticalInset, color)
        }
        return
    }
    fill(x + CORNER_RADIUS, y, x + width - CORNER_RADIUS, y + 1, color)
    fill(x + 1, y + 1, x + width - 1, y + CORNER_RADIUS, color)
    fill(x, y + CORNER_RADIUS, x + width, y + height - CORNER_RADIUS, color)
    fill(x + 1, y + height - CORNER_RADIUS, x + width - 1, y + height - 1, color)
    fill(x + CORNER_RADIUS, y + height - 1, x + width - CORNER_RADIUS, y + height, color)
}

internal fun roundedRectVerticalInset(horizontalOffset: Int, width: Int, height: Int): Int =
    if (height <= CORNER_RADIUS * 2) 0
    else (CORNER_RADIUS - minOf(horizontalOffset, width - horizontalOffset - 1)).coerceAtLeast(0)

internal fun GuiGraphicsExtractor.fillGlossyRoundedRect(x: Int, y: Int, width: Int, height: Int, color: Int) {
    fillRoundedRect(x, y, width, height, color)
    if (width <= CORNER_RADIUS * 2 || height <= CORNER_RADIUS * 2) return
    fill(x + CORNER_RADIUS, y + 1, x + width - CORNER_RADIUS, y + 2, ARGB.addRgb(color, GLOSS_HIGHLIGHT))
    fill(
        x + CORNER_RADIUS,
        y + height - 2,
        x + width - CORNER_RADIUS,
        y + height - 1,
        ARGB.subtractRgb(color, GLOSS_SHADE),
    )
}

internal fun GuiGraphicsExtractor.fillGlossyRect(x: Int, y: Int, width: Int, height: Int, color: Int) {
    fill(x, y, x + width, y + height, color)
    if (height <= CORNER_RADIUS * 2) return
    fill(x, y + 1, x + width, y + 2, ARGB.addRgb(color, GLOSS_HIGHLIGHT))
    fill(x, y + height - 2, x + width, y + height - 1, ARGB.subtractRgb(color, GLOSS_SHADE))
}

private const val INNER_PADDING = 1
private const val CORNER_RADIUS = 2
private const val GLOSS_HIGHLIGHT = 0x2A2A2A
private const val GLOSS_SHADE = 0x303030
