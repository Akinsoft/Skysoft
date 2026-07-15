package com.skysoft.features.inventory

import com.skysoft.config.InventoryButtonConfig
import com.skysoft.config.InventoryButtonDefaults.PLAYER_INVENTORY_HEIGHT
import com.skysoft.utils.gui.Point
import com.skysoft.utils.gui.Rect

internal data class InventoryButtonCanvas(
    val container: Rect,
    val playerInventory: Boolean,
) {
    private val verticalAnchor: Rect = run {
        if (playerInventory) return@run container
        val height = container.height.coerceAtMost(PLAYER_INVENTORY_HEIGHT)
        val top = container.y + ((container.height - PLAYER_INVENTORY_HEIGHT).coerceAtLeast(0) / 2)
        Rect(container.x, top, container.width, height)
    }

    fun position(button: InventoryButtonConfig): Point {
        val origin = origin(button)
        return Point(origin.x + button.x, origin.y + button.y)
    }

    fun move(button: InventoryButtonConfig, screenX: Int, screenY: Int) {
        val origin = origin(button)
        button.x = screenX - origin.x
        button.y = screenY - origin.y
    }

    fun overlapsContainer(buttonBounds: Rect): Boolean = !playerInventory && buttonBounds.intersects(container)

    private fun origin(button: InventoryButtonConfig): Point = Point(
        if (button.anchorRight) container.x + container.width else container.x,
        when {
            !button.anchorBottom -> verticalAnchor.y
            button.y >= 0 -> container.y + container.height
            else -> verticalAnchor.y + verticalAnchor.height
        },
    )
}
