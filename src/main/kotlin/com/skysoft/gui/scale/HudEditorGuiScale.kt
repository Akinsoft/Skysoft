package com.skysoft.gui.scale

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.gui.HudEditorElement
import com.skysoft.utils.gui.Rect
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft

internal class HudEditorGuiScale(private val hasInventoryScreen: Boolean) {
    fun normalRenderScale(): Float = normalGuiScale() / activeInventoryGuiScale().toFloat()

    fun usesInventoryCoordinates(element: HudEditorElement): Boolean =
        hasInventoryScreen && element.usesInventoryScale

    private fun normalCoordinate(coordinate: Int): Int =
        (coordinate * activeInventoryGuiScale() / normalGuiScale().toFloat()).roundToInt()

    fun elementMouseX(element: HudEditorElement, mouseX: Int): Int =
        if (usesInventoryCoordinates(element)) mouseX else normalCoordinate(mouseX)

    fun elementMouseY(element: HudEditorElement, mouseY: Int): Int =
        if (usesInventoryCoordinates(element)) mouseY else normalCoordinate(mouseY)

    fun elementScreenBounds(element: HudEditorElement, bounds: Rect): Rect {
        if (usesInventoryCoordinates(element)) return bounds
        val scale = normalRenderScale()
        val left = (bounds.x * scale).roundToInt()
        val top = (bounds.y * scale).roundToInt()
        val right = ((bounds.x + bounds.width) * scale).roundToInt()
        val bottom = ((bounds.y + bounds.height) * scale).roundToInt()
        return Rect(left, top, right - left, bottom - top)
    }

    fun <T> withElementGuiScale(element: HudEditorElement, block: () -> T): T =
        if (usesInventoryCoordinates(element)) withInventoryGuiScale(block) else withNormalGuiScale(block)

    fun <T> withNormalGuiScale(block: () -> T): T = withGuiScale(normalGuiScale(), block)

    private fun <T> withInventoryGuiScale(block: () -> T): T =
        withGuiScale(activeInventoryGuiScale(), block)

    private fun <T> withGuiScale(scale: Int, block: () -> T): T {
        val window = Minecraft.getInstance().window
        if (window.guiScale == scale) return block()
        return GuiScaleController.WindowScaleOverride.create(window, scale).use { block() }
    }

    private fun normalGuiScale(): Int {
        val minecraft = Minecraft.getInstance()
        val configuredScale = minecraft.options.guiScale().get()
        return minecraft.window.calculateScale(configuredScale, minecraft.isEnforceUnicode).coerceAtLeast(1)
    }

    private fun activeInventoryGuiScale(): Int {
        val minecraft = Minecraft.getInstance()
        val inventoryConfig = SkysoftConfigGui.config().gui.inventoryScreen
        if (!hasInventoryScreen || !shouldUseConfiguredInventoryScale(
                inventoryConfig.separateInventoryGuiScale,
                inventoryConfig.settings.isInventoryGuiScaleStorageOnly,
                isStorageOverlayActive = false,
            )
        ) return normalGuiScale()
        return minecraft.window.calculateScale(
            inventoryConfig.settings.inventoryGuiScale.coerceAtLeast(0),
            minecraft.isEnforceUnicode,
        ).coerceAtLeast(1)
    }
}
