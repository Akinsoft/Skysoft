package com.skysoft.config

object InventoryButtonDefaults {
    private const val DEFAULT_EDGE_BUTTONS = 8

    const val PLAYER_INVENTORY_HEIGHT = 166
    const val DEFAULT_BUTTON_COUNT = DEFAULT_EDGE_BUTTONS * 4

    fun create(): MutableList<InventoryButtonConfig> {
        val right = List(DEFAULT_EDGE_BUTTONS) { index ->
            val y = EDGE_BUTTON_START + EDGE_BUTTON_STEP * index
            val lowerHalf = y >= EDGE_BUTTON_BOTTOM_ANCHOR_THRESHOLD
            button(
                EDGE_BUTTON_RIGHT_X,
                if (lowerHalf) y - PLAYER_INVENTORY_HEIGHT else y,
                anchorRight = true,
                anchorBottom = lowerHalf,
            )
        }
        val top = List(DEFAULT_EDGE_BUTTONS) { index ->
            button(TOP_ROW_START_X + TOP_ROW_STEP * index, FLOATING_ROW_ABOVE_Y)
        }
        val left = List(DEFAULT_EDGE_BUTTONS) { index ->
            val y = EDGE_BUTTON_START + EDGE_BUTTON_STEP * index
            val lowerHalf = y >= EDGE_BUTTON_BOTTOM_ANCHOR_THRESHOLD
            button(
                FLOATING_COLUMN_LEFT_X,
                if (lowerHalf) y - PLAYER_INVENTORY_HEIGHT else y,
                anchorBottom = lowerHalf,
            )
        }
        val bottom = List(DEFAULT_EDGE_BUTTONS) { index ->
            button(TOP_ROW_START_X + TOP_ROW_STEP * index, EDGE_BUTTON_START, anchorBottom = true)
        }
        return (right + top + left + bottom).toMutableList()
    }

    fun resettableButtons(loadedButtons: MutableList<InventoryButtonConfig>): MutableList<InventoryButtonConfig> =
        loadedButtons.takeIf { it.isNotEmpty() } ?: create()

    private fun button(
        x: Int,
        y: Int,
        anchorRight: Boolean = false,
        anchorBottom: Boolean = false,
    ): InventoryButtonConfig = InventoryButtonConfig(
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
