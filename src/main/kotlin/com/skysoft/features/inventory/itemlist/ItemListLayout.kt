package com.skysoft.features.inventory.itemlist

import com.skysoft.config.ItemListSettingsConfig
import com.skysoft.config.ItemListSourcesConfig
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.core.HudPosition
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.gui.Rect
import kotlin.math.min
import kotlin.math.roundToInt
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

internal data class ItemListLayout(
    val panel: Rect,
    val favorites: Rect?,
    val grid: Rect,
    val previous: Rect,
    val next: Rect,
    val pageLabel: Rect,
    val config: Rect?,
    val search: Rect,
    val footer: Rect,
    val columns: Int,
    val rows: Int,
    val slotSize: Int,
    val itemScale: Float,
) {
    val pageSize: Int get() = columns * rows
    fun containsInteractive(mouseX: Int, mouseY: Int): Boolean =
        panel.contains(mouseX, mouseY) || footer.contains(mouseX, mouseY)

    fun slotBounds(index: Int): Rect = Rect(
        grid.x + index % columns * slotSize,
        grid.y + index / columns * slotSize,
        slotSize,
        slotSize,
    )

    fun favoriteBounds(index: Int): Rect? = favorites?.let {
        Rect(it.x + index * slotSize, it.y, slotSize, slotSize)
    }

    companion object {
        fun create(screen: AbstractContainerScreen<*>, hasFavorites: Boolean): ItemListLayout? {
            val accessor = screen as AbstractContainerScreenAccessor
            val containerRight = accessor.`skysoft$getLeftPos`() + accessor.`skysoft$getImageWidth`()
            val itemList = SkysoftConfigGui.config().inventory.itemList
            return create(
                screen.width,
                screen.height,
                containerRight,
                hasFavorites,
                itemList.sources.searchPosition,
                itemList.sources.isSettingsButtonHidden,
                itemList.settings.columns,
                itemList.settings.rows,
                itemList.settings.itemScale,
                itemList.sources.searchWidth,
            )
        }

        internal fun create(
            screenWidth: Int,
            screenHeight: Int,
            containerRight: Int,
            hasFavorites: Boolean,
            searchPosition: HudPosition = HudPosition(-OUTER_MARGIN, -OUTER_MARGIN, centerY = false).rememberDefault(),
            isSettingsButtonHidden: Boolean = false,
            maximumColumns: Int = ItemListSettingsConfig.DEFAULT_COLUMNS,
            maximumRows: Int = ItemListSettingsConfig.DEFAULT_ROWS,
            itemScale: Float = ItemListSettingsConfig.DEFAULT_ITEM_SCALE,
            independentSearchWidth: Int = ItemListSourcesConfig.DEFAULT_SEARCH_WIDTH,
        ): ItemListLayout? {
            val requestedSlotSize = (DEFAULT_SLOT_SIZE * itemScale).roundToInt()
            val right = screenWidth - OUTER_MARGIN
            val availableWidth = right - containerRight - CONTAINER_GAP
            val containerColumns = min(maximumColumns, availableWidth / DEFAULT_SLOT_SIZE)
            if (containerColumns < MIN_CONTAINER_SIZE) return null
            val panelWidth = containerColumns * DEFAULT_SLOT_SIZE
            val panelX = right - panelWidth
            val panelY = OUTER_MARGIN
            val isFooterMoved = !searchPosition.isAtDefault()
            val containerFavoritesHeight = if (hasFavorites) DEFAULT_SLOT_SIZE + SECTION_GAP else 0
            val defaultFooterY = screenHeight - OUTER_MARGIN - FIELD_HEIGHT
            val maximumNavigationY = if (isFooterMoved) {
                screenHeight - OUTER_MARGIN - BUTTON_HEIGHT
            } else {
                defaultFooterY - SECTION_GAP - BUTTON_HEIGHT
            }
            val availableContainerHeight = maximumNavigationY - SECTION_GAP - panelY - containerFavoritesHeight
            val availableContainerRows = availableContainerHeight / DEFAULT_SLOT_SIZE
            if (availableContainerRows < MIN_CONTAINER_SIZE) return null
            val containerRows = if (maximumRows == ItemListSettingsConfig.DEFAULT_ROWS) {
                availableContainerRows
            } else {
                min(maximumRows, availableContainerRows)
            }
            val navigationY = if (containerRows == availableContainerRows) {
                maximumNavigationY
            } else {
                panelY + containerFavoritesHeight + containerRows * DEFAULT_SLOT_SIZE + SECTION_GAP
            }
            val maximumSlotHeight = if (hasFavorites) {
                (navigationY - panelY - SECTION_GAP * 2) / 2
            } else {
                navigationY - panelY - SECTION_GAP
            }
            val slotSize = minOf(requestedSlotSize, panelWidth, maximumSlotHeight)
            val columns = panelWidth / slotSize
            if (columns < MIN_VISIBLE_SIZE) return null
            val favoritesHeight = if (hasFavorites) slotSize + SECTION_GAP else 0
            val gridY = panelY + favoritesHeight
            val rows = (navigationY - SECTION_GAP - gridY) / slotSize
            if (rows < MIN_VISIBLE_SIZE) return null
            val contentWidth = columns * slotSize
            val contentX = panelX + panelWidth - contentWidth
            val favoriteBounds = if (hasFavorites) Rect(contentX, panelY, contentWidth, slotSize) else null
            val grid = Rect(contentX, gridY, contentWidth, rows * slotSize)
            val navigationWidth = panelWidth
            val navigationButtonWidth = min(
                NAVIGATION_BUTTON_WIDTH,
                (navigationWidth - CONTROL_GAP * 2) / NAVIGATION_SECTION_COUNT,
            )
            val previous = Rect(panelX, navigationY, navigationButtonWidth, BUTTON_HEIGHT)
            val next = Rect(
                panelX + panelWidth - navigationButtonWidth,
                navigationY,
                navigationButtonWidth,
                BUTTON_HEIGHT,
            )
            val pageLabel = Rect(
                previous.x + previous.width + CONTROL_GAP,
                navigationY,
                navigationWidth - navigationButtonWidth * 2 - CONTROL_GAP * 2,
                BUTTON_HEIGHT,
            )
            val footerBounds = footerBounds(
                screenWidth,
                screenHeight,
                right,
                panelWidth,
                searchPosition,
                isSettingsButtonHidden,
                independentSearchWidth,
            )
            return ItemListLayout(
                panel = Rect(panelX, panelY, panelWidth, navigationY + BUTTON_HEIGHT - panelY),
                favorites = favoriteBounds,
                grid = grid,
                previous = previous,
                next = next,
                pageLabel = pageLabel,
                config = footerBounds.config,
                search = footerBounds.search,
                footer = footerBounds.footer,
                columns = columns,
                rows = rows,
                slotSize = slotSize,
                itemScale = slotSize.toFloat() / DEFAULT_SLOT_SIZE,
            )
        }

        const val DEFAULT_SLOT_SIZE = 18
        const val DEFAULT_FOOTER_WIDTH = ItemListSourcesConfig.DEFAULT_SEARCH_WIDTH
        const val FOOTER_HEIGHT = 20
        const val CONFIG_BUTTON_WIDTH = 24
        const val CONTROL_GAP = 3
        private const val MIN_CONTAINER_SIZE = 2
        private const val MIN_VISIBLE_SIZE = 1
        const val OUTER_MARGIN = 4
        private const val CONTAINER_GAP = 5
        private const val SECTION_GAP = 3
        private const val BUTTON_HEIGHT = 16
        private const val FIELD_HEIGHT = FOOTER_HEIGHT
        private const val NAVIGATION_BUTTON_WIDTH = 24
        private const val NAVIGATION_SECTION_COUNT = 3

        private fun footerBounds(
            screenWidth: Int,
            screenHeight: Int,
            right: Int,
            panelWidth: Int,
            searchPosition: HudPosition,
            isSettingsButtonHidden: Boolean,
            independentSearchWidth: Int,
        ): FooterBounds {
            val isMoved = !searchPosition.isAtDefault()
            val width = if (isMoved) {
                min(
                    independentSearchWidth.coerceAtLeast(ItemListSourcesConfig.MIN_SEARCH_WIDTH),
                    screenWidth - OUTER_MARGIN * 2,
                )
            } else {
                panelWidth
            }
            val x = if (isMoved) searchPosition.getAbsX0(screenWidth, width) else right - width
            val defaultY = screenHeight - OUTER_MARGIN - FIELD_HEIGHT
            val y = if (isMoved) searchPosition.getAbsY0(screenHeight, FIELD_HEIGHT) else defaultY
            val searchWidth = if (isSettingsButtonHidden) width else width - CONFIG_BUTTON_WIDTH - CONTROL_GAP
            val search = Rect(x, y, searchWidth, FIELD_HEIGHT)
            val config = if (isSettingsButtonHidden) {
                null
            } else {
                Rect(search.x + search.width + CONTROL_GAP, y, CONFIG_BUTTON_WIDTH, FIELD_HEIGHT)
            }
            return FooterBounds(Rect(x, y, width, FIELD_HEIGHT), search, config)
        }
    }
}

private data class FooterBounds(
    val footer: Rect,
    val search: Rect,
    val config: Rect?,
)
