package com.skysoft.features.inventory.itemlist

import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.utils.gui.Rect
import kotlin.math.min

internal data class ItemListViewerLayout(
    val panel: Rect,
    val item: Rect,
    val title: Rect,
    val back: Rect,
    val forward: Rect,
    val favorite: Rect,
    val close: Rect,
    val infoTab: Rect,
    val recipeTab: Rect,
    val usageTab: Rect,
    val content: Rect,
    val previous: Rect,
    val next: Rect,
    val recipePage: Rect,
    val wiki: Rect,
    val tierPrevious: Rect,
    val tierNext: Rect,
    val tierPage: Rect,
    val recipePageWithTiers: Rect,
) {
    val recipeGrid = ViewerCardGrid.create(
        Rect(
            content.x,
            content.y + ViewerTabDimensions.CATEGORY_AREA_HEIGHT,
            content.width,
            content.height - ViewerTabDimensions.CATEGORY_AREA_HEIGHT,
        ),
    )
    val entityGrid = ViewerCardGrid.compact(content)

    fun pageSize(key: ItemListEntryKey): Int =
        if (key.kind == ItemListEntryKind.ENTITY) entityGrid.pageSize else recipeGrid.pageSize

    fun category(index: Int): Rect {
        val width = (content.width - ViewerTabDimensions.CATEGORY_GAP * (ViewerTabDimensions.CATEGORY_COLUMNS - 1)) /
            ViewerTabDimensions.CATEGORY_COLUMNS
        return Rect(
            content.x + index % ViewerTabDimensions.CATEGORY_COLUMNS * (width + ViewerTabDimensions.CATEGORY_GAP),
            content.y + index / ViewerTabDimensions.CATEGORY_COLUMNS *
                (ViewerTabDimensions.CATEGORY_HEIGHT + ViewerTabDimensions.CATEGORY_GAP),
            width,
            ViewerTabDimensions.CATEGORY_HEIGHT,
        )
    }

    companion object {
        fun create(screenWidth: Int, screenHeight: Int): ItemListViewerLayout {
            val panelWidth = min(ViewerPanelDimensions.MAX_WIDTH, screenWidth - ViewerPanelDimensions.SCREEN_INSET)
                .coerceAtLeast(ViewerPanelDimensions.MIN_WIDTH)
            val panelHeight = min(ViewerPanelDimensions.MAX_HEIGHT, screenHeight - ViewerPanelDimensions.SCREEN_INSET)
                .coerceAtLeast(ViewerPanelDimensions.MIN_HEIGHT)
            val panel = Rect((screenWidth - panelWidth) / 2, (screenHeight - panelHeight) / 2, panelWidth, panelHeight)
            val item = Rect(
                panel.x + ViewerPanelDimensions.PADDING,
                panel.y + ViewerPanelDimensions.PADDING,
                ViewerPanelDimensions.SLOT_SIZE,
                ViewerPanelDimensions.SLOT_SIZE,
            )
            val title = Rect(
                item.x + ViewerHeaderDimensions.TITLE_X_OFFSET,
                panel.y + ViewerHeaderDimensions.TITLE_Y_OFFSET,
                panel.width - ViewerHeaderDimensions.TITLE_RESERVED_WIDTH,
                ViewerHeaderDimensions.HEIGHT,
            )
            val close = Rect(
                panel.x + panel.width - ViewerHeaderDimensions.CLOSE_RIGHT,
                panel.y + ViewerHeaderDimensions.BUTTON_TOP,
                ViewerHeaderDimensions.BUTTON_WIDTH,
                ViewerPanelDimensions.SLOT_SIZE,
            )
            val favorite = headerButtonBefore(close)
            val forward = headerButtonBefore(favorite)
            val back = headerButtonBefore(forward)
            val tabsY = panel.y + ViewerTabDimensions.Y_OFFSET
            val infoTab = Rect(
                panel.x + ViewerPanelDimensions.PADDING,
                tabsY,
                ViewerTabDimensions.WIDTH,
                ViewerTabDimensions.HEIGHT,
            )
            val recipeTab = tabAfter(infoTab)
            val usageTab = tabAfter(recipeTab)
            val footerY = panel.y + panel.height - ViewerFooterDimensions.BOTTOM
            val content = Rect(
                panel.x + ViewerPanelDimensions.PADDING,
                tabsY + ViewerTabDimensions.CONTENT_TOP_GAP,
                panel.width - ViewerPanelDimensions.SCREEN_INSET,
                footerY - tabsY - ViewerTabDimensions.CONTENT_FOOTER_GAP,
            )
            val footer = ViewerFooterLayout.create(panel, footerY)
            return ItemListViewerLayout(
                panel,
                item,
                title,
                back,
                forward,
                favorite,
                close,
                infoTab,
                recipeTab,
                usageTab,
                content,
                footer.previous,
                footer.next,
                footer.recipePage,
                footer.wiki,
                footer.tierPrevious,
                footer.tierNext,
                footer.tierPage,
                footer.recipePageWithTiers,
            )
        }

        private fun headerButtonBefore(button: Rect): Rect = Rect(
            button.x - ViewerHeaderDimensions.BUTTON_STEP,
            button.y,
            ViewerHeaderDimensions.BUTTON_WIDTH,
            ViewerPanelDimensions.SLOT_SIZE,
        )

        private fun tabAfter(tab: Rect): Rect = Rect(
            tab.x + ViewerTabDimensions.WIDTH + ViewerTabDimensions.GAP,
            tab.y,
            ViewerTabDimensions.WIDTH,
            ViewerTabDimensions.HEIGHT,
        )
    }
}

private data class ViewerFooterLayout(
    val previous: Rect,
    val next: Rect,
    val recipePage: Rect,
    val wiki: Rect,
    val tierPrevious: Rect,
    val tierNext: Rect,
    val tierPage: Rect,
    val recipePageWithTiers: Rect,
) {
    companion object {
        fun create(panel: Rect, footerY: Int): ViewerFooterLayout {
            val previous = Rect(
                panel.x + ViewerPanelDimensions.PADDING,
                footerY,
                ViewerFooterDimensions.PAGE_BUTTON_WIDTH,
                ViewerPanelDimensions.SLOT_SIZE,
            )
            val next = Rect(
                panel.x + panel.width - ViewerFooterDimensions.NEXT_RIGHT,
                footerY,
                ViewerFooterDimensions.PAGE_BUTTON_WIDTH,
                ViewerPanelDimensions.SLOT_SIZE,
            )
            val tierPage = Rect(
                panel.x + (panel.width - ViewerFooterDimensions.TIER_PAGE_WIDTH) / 2,
                footerY,
                ViewerFooterDimensions.TIER_PAGE_WIDTH,
                ViewerPanelDimensions.SLOT_SIZE,
            )
            val tierPrevious = Rect(
                tierPage.x - ViewerFooterDimensions.TIER_BUTTON_WIDTH - ViewerFooterDimensions.TIER_GAP,
                footerY,
                ViewerFooterDimensions.TIER_BUTTON_WIDTH,
                ViewerPanelDimensions.SLOT_SIZE,
            )
            val wiki = Rect(
                panel.x + ViewerPanelDimensions.PADDING,
                footerY,
                ViewerFooterDimensions.WIKI_WIDTH,
                ViewerPanelDimensions.SLOT_SIZE,
            )
            return ViewerFooterLayout(
                previous = previous,
                next = next,
                recipePage = Rect(
                    previous.x + ViewerFooterDimensions.PAGE_LABEL_X_OFFSET,
                    footerY,
                    panel.width - ViewerFooterDimensions.PAGE_LABEL_RESERVED_WIDTH,
                    ViewerPanelDimensions.SLOT_SIZE,
                ),
                wiki = wiki,
                tierPrevious = tierPrevious,
                tierNext = Rect(
                    tierPage.x + tierPage.width + ViewerFooterDimensions.TIER_GAP,
                    footerY,
                    ViewerFooterDimensions.TIER_BUTTON_WIDTH,
                    ViewerPanelDimensions.SLOT_SIZE,
                ),
                tierPage = tierPage,
                recipePageWithTiers = Rect(
                    previous.x + previous.width + ViewerFooterDimensions.TIER_GAP,
                    footerY,
                    (tierPrevious.x - previous.x - previous.width - ViewerFooterDimensions.TIER_GAP * 2)
                        .coerceAtLeast(1),
                    ViewerPanelDimensions.SLOT_SIZE,
                ),
            )
        }
    }
}

private object ViewerPanelDimensions {
    const val MAX_WIDTH = 430
    const val MIN_WIDTH = 300
    const val MAX_HEIGHT = 350
    const val MIN_HEIGHT = 220
    const val SCREEN_INSET = 20
    const val PADDING = 10
    const val SLOT_SIZE = 18
}

private object ViewerHeaderDimensions {
    const val TITLE_X_OFFSET = 24
    const val TITLE_Y_OFFSET = 9
    const val TITLE_RESERVED_WIDTH = 170
    const val HEIGHT = 30
    const val CLOSE_RIGHT = 30
    const val BUTTON_TOP = 8
    const val BUTTON_WIDTH = 22
    const val BUTTON_STEP = 26
}

private object ViewerTabDimensions {
    const val Y_OFFSET = 42
    const val WIDTH = 72
    const val HEIGHT = 19
    const val GAP = 4
    const val CONTENT_TOP_GAP = 24
    const val CONTENT_FOOTER_GAP = 28
    const val CATEGORY_GAP = 3
    const val CATEGORY_COLUMNS = 3
    const val CATEGORY_HEIGHT = 18
    const val CATEGORY_AREA_HEIGHT = CATEGORY_HEIGHT * 2 + CATEGORY_GAP + 4
}

private object ViewerFooterDimensions {
    const val BOTTOM = 30
    const val PAGE_BUTTON_WIDTH = 28
    const val NEXT_RIGHT = 38
    const val PAGE_LABEL_X_OFFSET = 32
    const val PAGE_LABEL_RESERVED_WIDTH = 84
    const val WIKI_WIDTH = 82
    const val TIER_PAGE_WIDTH = 52
    const val TIER_BUTTON_WIDTH = 22
    const val TIER_GAP = 4
}
