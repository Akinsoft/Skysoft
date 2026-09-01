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
        val relativeY = button.y + if (button.anchorBottom) verticalAnchor.height else 0
        val y = when {
            relativeY < 0 -> container.y + relativeY
            relativeY >= verticalAnchor.height -> container.y + container.height + relativeY - verticalAnchor.height
            else -> verticalAnchor.y + relativeY
        }
        return Point(
            (if (button.anchorRight) container.x + container.width else container.x) + button.x,
            y,
        )
    }

    fun move(button: InventoryButtonConfig, screenX: Int, screenY: Int) {
        button.x = screenX - if (button.anchorRight) container.x + container.width else container.x
        val relativeY = when {
            screenY < container.y -> screenY - container.y
            screenY >= container.y + container.height -> verticalAnchor.height + screenY - container.y - container.height
            else -> screenY - verticalAnchor.y
        }
        button.y = relativeY - if (button.anchorBottom) verticalAnchor.height else 0
    }

    fun overlapsContainer(buttonBounds: Rect): Boolean = !playerInventory && buttonBounds.intersects(container)
}
