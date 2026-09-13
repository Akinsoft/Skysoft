package com.skysoft.utils.gui

import kotlin.math.roundToInt
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack

data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun contains(mouseX: Int, mouseY: Int): Boolean = mouseX in x until x + width && mouseY in y until y + height
    fun intersects(other: Rect): Boolean =
        x < other.x + other.width &&
            x + width > other.x &&
            y < other.y + other.height &&
            y + height > other.y

    fun intersection(other: Rect): Rect? {
        val left = maxOf(x, other.x)
        val top = maxOf(y, other.y)
        val right = minOf(x + width, other.x + other.width)
        val bottom = minOf(y + height, other.y + other.height)
        return if (right > left && bottom > top) Rect(left, top, right - left, bottom - top) else null
    }

    fun interpolateTo(target: Rect, progress: Double): Rect {
        val normalizedProgress = progress.coerceIn(0.0, 1.0)
        return Rect(
            x = interpolateInt(x, target.x, normalizedProgress),
            y = interpolateInt(y, target.y, normalizedProgress),
            width = interpolateInt(width, target.width, normalizedProgress),
            height = interpolateInt(height, target.height, normalizedProgress),
        )
    }
}

data class Point(val x: Int, val y: Int)

internal fun interpolateInt(start: Int, end: Int, progress: Float): Int =
    interpolateInt(start, end, progress.toDouble())

internal fun interpolateInt(start: Int, end: Int, progress: Double): Int =
    (start + (end - start) * progress.coerceIn(0.0, 1.0)).roundToInt()

internal fun GuiGraphicsExtractor.itemWithDecorations(stack: ItemStack, x: Int, y: Int) {
    if (stack.isEmpty) return
    item(stack, x, y)
    itemDecorations(net.minecraft.client.Minecraft.getInstance().font, stack, x, y)
}
