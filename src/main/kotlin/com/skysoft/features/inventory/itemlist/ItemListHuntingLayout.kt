package com.skysoft.features.inventory.itemlist

import com.skysoft.utils.gui.Rect

internal data class ItemListHuntingLayout(
    val page: Int,
    val pageSize: Int,
    val pageCount: Int,
    val cards: List<Rect>,
    val previous: Rect,
    val next: Rect,
    val pageLabel: Rect,
) {
    companion object {
        fun create(bounds: Rect, sourceCount: Int, requestedPage: Int): ItemListHuntingLayout {
            require(sourceCount > 0)
            val columns = sourceCount.coerceAtMost(HUNTING_COLUMNS)
            val rowsWithoutNavigation = visibleRows(bounds.height, navigationHeight = 0)
            val needsNavigation = sourceCount > columns * rowsWithoutNavigation
            val rows = visibleRows(bounds.height, if (needsNavigation) NAVIGATION_HEIGHT else 0)
            val pageSize = columns * rows
            val pageCount = Math.ceilDiv(sourceCount, pageSize)
            val page = requestedPage.coerceIn(0, pageCount - 1)
            val visibleCount = minOf(pageSize, sourceCount - page * pageSize)
            val width = (bounds.width - HUNTING_INSET * 2 - HUNTING_GAP * (columns - 1)) / columns
            val cards = List(visibleCount) { index ->
                Rect(
                    bounds.x + HUNTING_INSET + index % columns * (width + HUNTING_GAP),
                    bounds.y + HUNTING_TOP + index / columns * (HUNTING_CARD_HEIGHT + HUNTING_GAP),
                    width,
                    HUNTING_CARD_HEIGHT,
                )
            }
            val navigationY = bounds.y + bounds.height - NAVIGATION_CONTROL_HEIGHT
            val previous = Rect(bounds.x + HUNTING_INSET, navigationY, NAVIGATION_CONTROL_WIDTH, NAVIGATION_CONTROL_HEIGHT)
            val next = Rect(
                bounds.x + bounds.width - HUNTING_INSET - NAVIGATION_CONTROL_WIDTH,
                navigationY,
                NAVIGATION_CONTROL_WIDTH,
                NAVIGATION_CONTROL_HEIGHT,
            )
            return ItemListHuntingLayout(
                page,
                pageSize,
                pageCount,
                cards,
                previous,
                next,
                Rect(previous.x + previous.width, navigationY, next.x - previous.x - previous.width, NAVIGATION_CONTROL_HEIGHT),
            )
        }

        private fun visibleRows(height: Int, navigationHeight: Int): Int =
            ((height - HUNTING_TOP - navigationHeight + HUNTING_GAP) / (HUNTING_CARD_HEIGHT + HUNTING_GAP))
                .coerceAtLeast(1)

        private const val HUNTING_COLUMNS = 3
        private const val HUNTING_INSET = 8
        private const val HUNTING_TOP = 18
        private const val HUNTING_GAP = 3
        private const val HUNTING_CARD_HEIGHT = 28
        private const val NAVIGATION_HEIGHT = 21
        private const val NAVIGATION_CONTROL_WIDTH = 24
        private const val NAVIGATION_CONTROL_HEIGHT = 18
    }
}

internal fun huntingSourceBounds(bounds: Rect, sourceCount: Int): List<Rect> =
    ItemListHuntingLayout.create(bounds, sourceCount, requestedPage = 0).cards
