package com.skysoft.features.inventory

import com.skysoft.config.INVENTORY_BUTTON_SCALE_STEP
import com.skysoft.config.InventoryButtonConfig
import com.skysoft.config.InventoryButtonDefaults
import com.skysoft.config.MAX_INVENTORY_BUTTON_SCALE
import com.skysoft.config.MIN_INVENTORY_BUTTON_SCALE
import com.skysoft.config.normalizedInventoryButtonScale
import com.skysoft.utils.gui.Rect
import kotlin.math.roundToInt

internal object InventoryButtonLayout {
    internal val editorAddButtonBounds = Rect(
        EDITOR_INVENTORY_WIDTH + EDITOR_ADD_BUTTON_GAP,
        EDITOR_ADD_BUTTON_Y,
        BUTTON_SIZE,
        BUTTON_SIZE,
    )

    fun buttonBounds(canvas: InventoryButtonCanvas, button: InventoryButtonConfig): Rect {
        val point = canvas.position(button)
        val size = inventoryButtonSizeAtScale(button.scale)
        val growth = size - BUTTON_SIZE
        val x = if (point.x + BUTTON_SIZE <= canvas.container.x) point.x - growth else point.x
        val y = if (point.y + BUTTON_SIZE <= canvas.container.y) point.y - growth else point.y
        return Rect(x, y, size, size)
    }

    fun moveButton(
        canvas: InventoryButtonCanvas,
        button: InventoryButtonConfig,
        screenX: Int,
        screenY: Int,
    ) {
        val size = inventoryButtonSizeAtScale(button.scale)
        val growth = size - BUTTON_SIZE
        val baseX = if (screenX + size <= canvas.container.x) screenX + growth else screenX
        val baseY = if (screenY + size <= canvas.container.y) screenY + growth else screenY
        canvas.move(button, baseX, baseY)
    }

    fun nudgeButton(
        canvas: InventoryButtonCanvas,
        button: InventoryButtonConfig,
        deltaX: Int,
        deltaY: Int,
    ) {
        val bounds = buttonBounds(canvas, button)
        moveButton(canvas, button, bounds.x + deltaX, bounds.y + deltaY)
    }

    fun nextEditorButtonSlot(buttons: List<InventoryButtonConfig>): InventoryButtonConfig {
        val canvas = InventoryButtonCanvas(
            Rect(0, 0, EDITOR_INVENTORY_WIDTH, InventoryButtonDefaults.PLAYER_INVENTORY_HEIGHT),
            playerInventory = true,
        )
        val occupied = buttons.map { button -> buttonBounds(canvas, button) }
        var x = editorAddButtonBounds.x + editorAddButtonBounds.width + EDITOR_BUTTON_GAP
        while (occupied.any { it.intersects(Rect(x, EDITOR_ADD_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE)) }) {
            x += BUTTON_SIZE + EDITOR_BUTTON_GAP
        }
        return InventoryButtonConfig(x = x, y = EDITOR_ADD_BUTTON_Y, isUserCreated = true)
    }

    const val BUTTON_SIZE = 18
    private const val EDITOR_INVENTORY_WIDTH = 176
    private const val EDITOR_ADD_BUTTON_GAP = 2
    private const val EDITOR_ADD_BUTTON_Y = -19
    private const val EDITOR_BUTTON_GAP = 2
}

internal fun inventoryButtonScaleAfterScroll(scale: Float, scrollY: Double): Float {
    val current = normalizedInventoryButtonScale(scale)
    if (scrollY == 0.0) return current
    val direction = if (scrollY > 0.0) 1 else -1
    val stepsPerUnit = (1f / INVENTORY_BUTTON_SCALE_STEP).roundToInt()
    val steps = (current * stepsPerUnit).roundToInt() + direction
    return (steps / stepsPerUnit.toFloat())
        .coerceIn(MIN_INVENTORY_BUTTON_SCALE, MAX_INVENTORY_BUTTON_SCALE)
}

internal fun inventoryButtonSizeAtScale(scale: Float): Int =
    (InventoryButtonLayout.BUTTON_SIZE * normalizedInventoryButtonScale(scale)).roundToInt().coerceAtLeast(1)
