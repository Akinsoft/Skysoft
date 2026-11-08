package com.skysoft.gui

import com.skysoft.utils.input.InputHandlingResult

interface TopLeftHudEditorElement : HudEditorElement {
    override fun absoluteX(width: Int): Int = position.getAbsX0AllowingOverflow(0)

    override fun absoluteY(height: Int): Int = position.getAbsY0AllowingOverflow(0)

    override fun applyEditorDrag(deltaX: Int, deltaY: Int): InputHandlingResult {
        position.moveToAbsoluteAllowingOverflow(absoluteX(0) + deltaX, absoluteY(0) + deltaY, 0, 0)
        return InputHandlingResult.CONSUMED
    }

    override fun applyEditorScroll(scrollY: Double): InputHandlingResult {
        position.scaleByScroll(scrollY)
        return InputHandlingResult.CONSUMED
    }
}
