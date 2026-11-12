package com.skysoft.features.inventory.itemlist

import com.skysoft.utils.gui.Rect
import net.minecraft.client.gui.GuiGraphicsExtractor

internal object ItemListSlotStyle {
    val BORDER = 0xFF111315.toInt()
    val FILL = 0xD0202428.toInt()

    fun draw(context: GuiGraphicsExtractor, bounds: Rect, fill: Int = FILL, border: Int = BORDER) {
        context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, border)
        context.fill(bounds.x + 1, bounds.y + 1, bounds.x + bounds.width - 1, bounds.y + bounds.height - 1, fill)
    }
}

internal const val ITEM_LIST_CHROME_FILL = 0xC012161A.toInt()
internal const val ITEM_LIST_SEARCH_BACKGROUND = 0xF014181C.toInt()
internal const val ITEM_LIST_SEARCH_FOCUS = 0xFF65C2FF.toInt()
internal const val ITEM_LIST_SEARCH_IDLE = 0xFF465058.toInt()

internal fun itemListSearchOutline(focused: Boolean, highlight: Int?): Int =
    highlight ?: if (focused) ITEM_LIST_SEARCH_FOCUS else ITEM_LIST_SEARCH_IDLE

internal val ITEM_LIST_TEXT_COLOR = 0xFFE0E4E8.toInt()
