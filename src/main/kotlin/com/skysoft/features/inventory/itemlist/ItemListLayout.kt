package com.skysoft.features.inventory.itemlist

import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.core.HudPosition
import com.skysoft.utils.gui.Rect
import kotlin.math.min
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
) {
    val pageSize: Int get() = columns * rows
    fun containsInteractive(mouseX: Int, mouseY: Int): Boolean =
        panel.contains(mouseX, mouseY) || footer.contains(mouseX, mouseY)

    fun slotBounds(index: Int): Rect = Rect(
        grid.x + index % columns * SLOT_SIZE,
        grid.y + index / columns * SLOT_SIZE,
        SLOT_SIZE,
        SLOT_SIZE,
    )

    fun favoriteBounds(index: Int): Rect? = favorites?.let {
        Rect(it.x + index * SLOT_SIZE, it.y, SLOT_SIZE, SLOT_SIZE)
    }

    companion object {
        fun create(screen: AbstractContainerScreen<*>, hasFavorites: Boolean): ItemListLayout? {
            val accessor = screen as AbstractContainerScreenAccessor
            val containerRight = accessor.`skysoft$getLeftPos`() + accessor.`skysoft$getImageWidth`()
            val details = SkysoftConfigGui.config().inventory.itemList.sources
            return create(
                screen.width,
                screen.height,
                containerRight,
                hasFavorites,
                details.searchPosition,
                details.isSettingsButtonHidden,
            )
        }

        internal fun create(
            screenWidth: Int,
            screenHeight: Int,
            containerRight: Int,
            hasFavorites: Boolean,
            searchPosition: HudPosition = HudPosition(-OUTER_MARGIN, -OUTER_MARGIN, centerY = false).rememberDefault(),
            isSettingsButtonHidden: Boolean = false,
        ): ItemListLayout? {
            val right = screenWidth - OUTER_MARGIN
            val availableWidth = right - containerRight - CONTAINER_GAP
            val columns = min(MAX_COLUMNS, availableWidth / SLOT_SIZE)
            if (columns < MIN_COLUMNS) return null
            val panelWidth = columns * SLOT_SIZE
            val panelX = right - panelWidth
            val panelY = OUTER_MARGIN
            val isFooterMoved = !searchPosition.isAtDefault()
            val favoritesHeight = if (hasFavorites) SLOT_SIZE + SECTION_GAP else 0
            val defaultFooterY = screenHeight - OUTER_MARGIN - FIELD_HEIGHT
            val navigationY = if (isFooterMoved) {
                screenHeight - OUTER_MARGIN - BUTTON_HEIGHT
            } else {
                defaultFooterY - SECTION_GAP - BUTTON_HEIGHT
            }
            val gridY = panelY + favoritesHeight
            val gridHeight = navigationY - SECTION_GAP - gridY
            val rows = gridHeight / SLOT_SIZE
            if (rows < MIN_ROWS) return null
            val favoriteBounds = if (hasFavorites) Rect(panelX, panelY, panelWidth, SLOT_SIZE) else null
            val grid = Rect(panelX, gridY, panelWidth, rows * SLOT_SIZE)
            val navigationWidth = panelWidth
            val previous = Rect(panelX, navigationY, NAVIGATION_BUTTON_WIDTH, BUTTON_HEIGHT)
            val next = Rect(right - NAVIGATION_BUTTON_WIDTH, navigationY, NAVIGATION_BUTTON_WIDTH, BUTTON_HEIGHT)
            val pageLabel = Rect(
                previous.x + previous.width + BUTTON_GAP,
                navigationY,
                navigationWidth - NAVIGATION_BUTTON_WIDTH * 2 - BUTTON_GAP * 2,
                BUTTON_HEIGHT,
            )
            val footerX = if (isFooterMoved) searchPosition.getAbsX0(screenWidth, panelWidth) else panelX
            val footerY = if (isFooterMoved) searchPosition.getAbsY0(screenHeight, FIELD_HEIGHT) else defaultFooterY
            val searchWidth = if (isSettingsButtonHidden) panelWidth else panelWidth - CONFIG_BUTTON_WIDTH - BUTTON_GAP
            val search = Rect(
                footerX,
                footerY,
                searchWidth,
                FIELD_HEIGHT,
            )
            val config = if (isSettingsButtonHidden) {
                null
            } else {
                Rect(search.x + search.width + BUTTON_GAP, footerY, CONFIG_BUTTON_WIDTH, FIELD_HEIGHT)
            }
            return ItemListLayout(
                panel = Rect(panelX, panelY, panelWidth, navigationY + BUTTON_HEIGHT - panelY),
                favorites = favoriteBounds,
                grid = grid,
                previous = previous,
                next = next,
                pageLabel = pageLabel,
                config = config,
                search = search,
                footer = Rect(footerX, footerY, panelWidth, FIELD_HEIGHT),
                columns = columns,
                rows = rows,
            )
        }

        const val SLOT_SIZE = 18
        private const val MAX_COLUMNS = 9
        private const val MIN_COLUMNS = 2
        private const val MIN_ROWS = 2
        private const val OUTER_MARGIN = 4
        private const val CONTAINER_GAP = 5
        private const val SECTION_GAP = 3
        private const val BUTTON_GAP = 3
        private const val BUTTON_HEIGHT = 16
        private const val FIELD_HEIGHT = 20
        private const val NAVIGATION_BUTTON_WIDTH = 24
        private const val CONFIG_BUTTON_WIDTH = 24
    }
}
