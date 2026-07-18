package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorage

internal object StorageSlots {
    const val SIZE = 18
    const val INNER_SIZE = 16
    const val BORDER = 1
}

internal object StoragePages {
    const val COLUMNS = ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES
    const val WIDTH = COLUMNS * StorageSlots.SIZE + 4
    const val PADDING = 8
    const val EMPTY_HEIGHT = 48
    const val TITLE_SLOT_GAP = 7
    const val SLOT_X_OFFSET = 3
    const val CONTENT_TOP_PADDING = 10
}

internal object StoragePanel {
    const val PADDING = 10
    const val EDGE_MARGIN = 4
    const val VERTICAL_RESERVED_SPACE = 48
    const val TOP_MIN = 8
}

internal object StorageScrollbar {
    const val GAP = 4
    const val WIDTH = 10
    const val MIN_KNOB_HEIGHT = 18
}

internal object StoragePlayerInventory {
    const val WIDTH = 176
    const val HEIGHT = 90
    const val HOTBAR_Y_OFFSET = 67
    const val SLOT_X_OFFSET = 7
    const val INVENTORY_Y_OFFSET = 9
    const val HOTBAR_SLOT_COUNT = 9
    const val OFFHAND_SWAP_BUTTON = 40
}

internal object StorageSearch {
    const val HEIGHT = 18
    const val GAP = 4
}

internal object StorageTitle {
    const val MAX_LENGTH = 64
    const val X_OFFSET = 5
    const val Y_OFFSET = 4
    const val MAX_WIDTH_INSET = 10
    const val BOUNDS_HEIGHT = 10
    const val BOX_END_INSET = 4
    const val BOX_WIDTH_INSET = 8
    const val EDIT_PADDING = 2
    const val CURSOR_MIN_Y_OFFSET = 3
    const val CURSOR_MAX_Y_OFFSET = 15
    const val CURSOR_RIGHT_INSET = 6
    const val CURSOR_BLINK_MILLIS = 500L
    const val CURSOR_BLINK_PHASES = 2L
    const val UNLOADED_PAGE_TEXT_Y_OFFSET = 22
}

internal object StorageSelector {
    const val COLUMNS = ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES
    const val ROWS = 4
    const val SLOT_SIZE = StorageSlots.SIZE
    const val WIDTH = StoragePlayerInventory.WIDTH
    const val HEIGHT = 100
    const val SLOT_OFFSET_X = (WIDTH - COLUMNS * SLOT_SIZE) / 2
    const val SLOT_OFFSET_Y = 20
    const val SIDE_GAP = 10
    const val STACKED_GAP = 4
    const val TEXT_X_OFFSET = 5
    const val TEXT_Y_OFFSET = 4
}

internal object StorageToolkit {
    const val FARMING_PAGE_INDEX = -1
    const val HUNTING_PAGE_INDEX = -2
    const val FARMING_SELECTOR_SLOT = 30
    const val HUNTING_SELECTOR_SLOT = 32
}

internal object StorageTextInput {
    const val PRINTABLE_ASCII_START = 32
    const val PRINTABLE_ASCII_END = 126
    const val EXTENDED_TEXT_START = 160
}

internal object StorageRuntime {
    const val OFFSCREEN = -100000
    const val MAX_ITEM_NBT_BYTES = 1_000_000L
    const val COMMAND_COOLDOWN_MILLIS = 100L
    const val OVERVIEW_SHORTCUT_TIMEOUT_MILLIS = 3_000L
    const val SCROLL_RESPONSE_MILLIS = 55.0
    const val MAX_SCROLL_FRAME_MILLIS = 100.0
    const val SCROLL_SETTLE_DISTANCE = 0.05
}

internal object StorageColors {
    val SELECTED = 0xFF55FFFF.toInt()
    val SLOT_HOVER = 0x80FFFFFF.toInt()
    val PANEL = 0xE0101010.toInt()
    val PANEL_OUTLINE = 0xFF505050.toInt()
    val PAGE_PANEL = 0xD0202020.toInt()
    val PAGE_PANEL_OUTLINE = 0xFF404040.toInt()
    val TITLE_EDIT_BACKGROUND = 0xA0080808.toInt()
    val TEXT_WHITE = 0xFFFFFFFF.toInt()
    val SCROLLBAR_TRACK = 0xFF242424.toInt()
    val SCROLLBAR_OUTLINE = 0xFF383838.toInt()
    val SCROLLBAR_KNOB = 0xFF808080.toInt()
    val SEARCH_BACKGROUND = 0xE0181818.toInt()
    val PLAYER_PANEL = 0xD0181818.toInt()
    val SELECTOR_SLOT = 0xFF101010.toInt()
    val SLOT_BACKGROUND = 0xFF303030.toInt()
}
