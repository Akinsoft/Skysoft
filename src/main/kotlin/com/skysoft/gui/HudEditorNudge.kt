package com.skysoft.gui

import com.mojang.blaze3d.platform.InputConstants
import com.skysoft.utils.gui.Point
import com.skysoft.utils.input.InputHandlingResult

internal fun hudEditorNudge(key: Int): Point? = when (key) {
    InputConstants.KEY_LEFT -> Point(-1, 0)
    InputConstants.KEY_RIGHT -> Point(1, 0)
    InputConstants.KEY_UP -> Point(0, -1)
    InputConstants.KEY_DOWN -> Point(0, 1)
    else -> null
}

internal fun HudEditorElement.nudgeInEditor(delta: Point): InputHandlingResult {
    val width = scaledWidth()
    val height = scaledHeight()
    beginEditorDrag(width / 2, height / 2, width, height)
    val customResult = applyEditorDrag(delta.x, delta.y)
    if (customResult == InputHandlingResult.CONSUMED || !canMove) return customResult
    moveInEditor(absoluteX(width) + delta.x, absoluteY(height) + delta.y, width, height)
    return InputHandlingResult.CONSUMED
}
