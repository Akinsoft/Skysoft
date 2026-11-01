package com.skysoft.config

object InventoryButtonDefaults {
    private const val DEFAULT_EDGE_BUTTONS = 8

    const val PLAYER_INVENTORY_HEIGHT = 166
    const val DEFAULT_BUTTON_COUNT = DEFAULT_EDGE_BUTTONS * 4

    fun create(): MutableList<InventoryButtonConfig> {
        val right = column(EDGE_BUTTON_RIGHT_X, anchorRight = true)
        val top = row(FLOATING_ROW_ABOVE_Y, anchorBottom = false)
        val left = column(FLOATING_COLUMN_LEFT_X, anchorRight = false)
        val bottom = row(EDGE_BUTTON_START, anchorBottom = true)
        return (right + top + left + bottom).toMutableList()
    }

    private fun column(x: Int, anchorRight: Boolean): List<InventoryButtonConfig> = List(DEFAULT_EDGE_BUTTONS) { index ->
        val y = EDGE_BUTTON_START + EDGE_BUTTON_STEP * index
        val lowerHalf = y >= EDGE_BUTTON_BOTTOM_ANCHOR_THRESHOLD
        button(x, if (lowerHalf) y - PLAYER_INVENTORY_HEIGHT else y, anchorRight, anchorBottom = lowerHalf)
    }

    private fun row(y: Int, anchorBottom: Boolean): List<InventoryButtonConfig> = List(DEFAULT_EDGE_BUTTONS) { index ->
        button(TOP_ROW_START_X + TOP_ROW_STEP * index, y, anchorRight = false, anchorBottom)
    }

    private fun button(x: Int, y: Int, anchorRight: Boolean, anchorBottom: Boolean) = InventoryButtonConfig(
        x = x,
        y = y,
        anchorRight = anchorRight,
        anchorBottom = anchorBottom,
        isUserCreated = false,
    )

    private const val EDGE_BUTTON_START = 2
    private const val EDGE_BUTTON_STEP = 20
    private const val EDGE_BUTTON_BOTTOM_ANCHOR_THRESHOLD = 80
    private const val EDGE_BUTTON_RIGHT_X = 2
    private const val TOP_ROW_START_X = 4
    private const val TOP_ROW_STEP = 21
    private const val FLOATING_ROW_ABOVE_Y = -19
    private const val FLOATING_COLUMN_LEFT_X = -19
}
