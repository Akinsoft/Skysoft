package com.skysoft.gui

import com.skysoft.config.core.HudPosition
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.gui.GuiGraphicsExtractor

object HudEditorRegistry {
    private val elements = linkedMapOf<String, HudEditorElement>()
    private val providers = linkedMapOf<String, () -> List<HudEditorElement>>()

    fun register(element: HudEditorElement) {
        requireCanRegister(element)
        elements[element.id] = element
    }

    internal fun requireCanRegister(element: HudEditorElement) {
        require(element.id !in elements) { "Duplicate HUD editor element id: ${element.id}" }
    }

    fun registerProvider(id: String, provider: () -> List<HudEditorElement>) {
        require(id !in providers) { "Duplicate HUD editor provider id: $id" }
        providers[id] = provider
    }

    fun visibleElements(hasInventoryScreen: Boolean = false): List<HudEditorElement> {
        val providedElements = providers.flatMap { (id, provider) ->
            SkysoftErrorBoundary.value("HUD editor provider $id", emptyList(), provider)
        }
        val allElements = elements.values + providedElements
        val duplicateId = allElements.groupingBy(HudEditorElement::id).eachCount().entries
            .firstOrNull { (_, count) -> count > 1 }
            ?.key
        require(duplicateId == null) { "Duplicate HUD editor element id: $duplicateId" }
        return allElements.filter { element ->
            element.isVisible() && (hasInventoryScreen || !element.requiresInventoryScreen)
        }
    }
}

interface HudEditorElement {
    val id: String
    val label: String
    val position: HudPosition
    val canMove: Boolean get() = true
    val canScale: Boolean get() = true
    val canResizeWidth: Boolean get() = false
    val canResizeHeight: Boolean get() = false
    val hasEditorBackground: Boolean get() = true
    val keepsInsideScreen: Boolean get() = false
    val layoutOffsetX: Int get() = 0
    val layoutOffsetY: Int get() = 0
    val snapGroup: String? get() = null
    val editorLeftPadding: Int get() = 0
    val editorSelectionPriority: Int get() = 0
    val editorGridSpacing: Int get() = HUD_EDITOR_GRID_SPACING
    val usesInventoryScale: Boolean get() = false
    val requiresInventoryScreen: Boolean get() = false

    fun width(): Int
    fun height(): Int
    fun isVisible(): Boolean
    fun renderEditor(context: GuiGraphicsExtractor)
    fun absoluteX(width: Int): Int = layoutOffsetX + if (keepsInsideScreen) {
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
    fun resizeEditor(width: Int, height: Int) = Unit
    fun minEditorWidth(): Int = 1
    fun minEditorHeight(): Int = 1
    fun resetEditorState() = position.resetToDefault()
    fun captureEditorState(): HudEditorSnapshot {
        val target = position
        val snapshot = target.snapshot()
        return hudEditorSnapshot(snapshot) { target.restore(snapshot) }
    }
    fun editorDetailsLines(): List<String>? = null
    fun editorActionLines(): List<String>? = null
    fun openConfig() = Unit
}

class HudEditorSnapshot internal constructor(
    private val value: Any?,
    private val restoreAction: () -> Unit,
) {
    internal fun hasSameValue(other: HudEditorSnapshot): Boolean = value == other.value

    internal fun restore() = restoreAction()
}

internal fun hudEditorSnapshot(value: Any?, restore: () -> Unit): HudEditorSnapshot =
    HudEditorSnapshot(value, restore)
