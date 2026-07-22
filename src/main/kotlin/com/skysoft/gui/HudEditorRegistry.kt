package com.skysoft.gui

import com.skysoft.config.core.HudPosition
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.gui.GuiGraphicsExtractor

object HudEditorRegistry {
    private val elements = linkedMapOf<String, HudEditorElement>()

    fun register(element: HudEditorElement) {
        elements[element.id] = element
    }

    fun visibleElements(hasInventoryScreen: Boolean = false): List<HudEditorElement> =
        elements.values.filter {
            it.isVisible() && (hasInventoryScreen || !it.requiresInventoryScreen)
        }
}

interface HudEditorElement {
    val id: String
    val label: String
    val position: HudPosition
    val canMove: Boolean get() = true
    val canScale: Boolean get() = true
    val hasEditorBackground: Boolean get() = true
    val keepsInsideScreen: Boolean get() = false
    val layoutOffsetY: Int get() = 0
    val editorLeftPadding: Int get() = 0
    val usesInventoryScale: Boolean get() = false
    val requiresInventoryScreen: Boolean get() = false

    fun width(): Int
    fun height(): Int
    fun isVisible(): Boolean
    fun renderDummy(context: GuiGraphicsExtractor)
    fun renderEditorDummy(context: GuiGraphicsExtractor) = renderDummy(context)
    fun absoluteX(width: Int): Int = if (keepsInsideScreen) {
        position.getAbsX0(width)
    } else {
        position.getAbsX0AllowingOverflow(width)
    }
    fun absoluteY(height: Int): Int = layoutOffsetY + if (keepsInsideScreen) {
        position.getAbsY0(height)
    } else {
        position.getAbsY0AllowingOverflow(height)
    }
    fun beginEditorDrag(localX: Int, localY: Int, width: Int, height: Int) = Unit
    fun applyEditorDrag(deltaX: Int, deltaY: Int): InputHandlingResult = InputHandlingResult.IGNORED
    fun applyEditorScroll(scrollY: Double): InputHandlingResult = InputHandlingResult.IGNORED
    fun resetEditorState() = position.resetToDefault()
    fun editorTooltipLines(): List<String>? = null
    fun openConfig() = Unit
}
