package com.skysoft.gui

import com.skysoft.utils.gui.Point
import com.skysoft.utils.input.InputHandlingResult
import kotlin.math.roundToInt
import net.minecraft.client.input.KeyEvent
import org.lwjgl.glfw.GLFW

internal fun KeyEvent.isHudEditorHistoryKey(): Boolean =
    hasControlDownWithQuirk() && key() in listOf(GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_Y)

internal fun hudEditorNudge(key: Int): Point? = when (key) {
    GLFW.GLFW_KEY_LEFT -> Point(-1, 0)
    GLFW.GLFW_KEY_RIGHT -> Point(1, 0)
    GLFW.GLFW_KEY_UP -> Point(0, -1)
    GLFW.GLFW_KEY_DOWN -> Point(0, 1)
    else -> null
}

internal fun HudEditorElement.nudgeInEditor(delta: Point): InputHandlingResult {
    val scale = position.effectiveScale
    val width = (width() * scale).roundToInt()
    val height = (height() * scale).roundToInt()
    beginEditorDrag(width / 2, height / 2, width, height)
    val customResult = applyEditorDrag(delta.x, delta.y)
    if (customResult == InputHandlingResult.CONSUMED || !canMove) return customResult
    val positionX = absoluteX(width) + delta.x - layoutOffsetX
    val positionY = absoluteY(height) + delta.y - layoutOffsetY
    if (keepsInsideScreen) position.moveToAbsolute(positionX, positionY, width, height)
    else position.moveToAbsoluteAllowingOverflow(positionX, positionY, width, height)
    return InputHandlingResult.CONSUMED
}
