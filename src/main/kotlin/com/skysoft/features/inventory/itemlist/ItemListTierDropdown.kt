package com.skysoft.features.inventory.itemlist

import com.skysoft.data.skyblock.ItemListEntry
import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.utils.gui.Rect
import net.minecraft.client.gui.GuiGraphicsExtractor

internal data class ItemListTierDropdown(
    val bounds: Rect,
    val tierBounds: List<Rect>,
) {
    fun renderBackground(context: GuiGraphicsExtractor) {
        context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, DROPDOWN_BORDER)
        context.fill(
            bounds.x + 1,
            bounds.y + 1,
            bounds.x + bounds.width - 1,
            bounds.y + bounds.height - 1,
            DROPDOWN_FILL,
        )
    }

    companion object {
        fun create(panel: Rect, anchor: Rect, tierCount: Int, requestedSlotSize: Int): ItemListTierDropdown {
            require(tierCount > 0)
            require(requestedSlotSize > 0)
            var columns = 1
            var slotSize = 0
            for (candidateColumns in 1..tierCount.coerceAtMost(MAX_COLUMNS)) {
                val candidateRows = Math.ceilDiv(tierCount, candidateColumns)
                val candidateSlotSize = minOf(
                    requestedSlotSize,
                    (panel.width - PADDING * 2) / candidateColumns,
                    (panel.height - PADDING * 2) / candidateRows,
                )
                if (candidateSlotSize >= slotSize) {
                    columns = candidateColumns
                    slotSize = candidateSlotSize
                }
            }
            require(slotSize > 0)
            val rows = Math.ceilDiv(tierCount, columns)
            val width = columns * slotSize + PADDING * 2
            val height = rows * slotSize + PADDING * 2
            val x = (anchor.x + anchor.width / 2 - width / 2).coerceIn(panel.x, panel.x + panel.width - width)
            val below = anchor.y + anchor.height + GAP
            val y = if (below + height <= panel.y + panel.height) below else anchor.y - GAP - height
            val bounds = Rect(x, y.coerceIn(panel.y, panel.y + panel.height - height), width, height)
            return ItemListTierDropdown(
                bounds,
                List(tierCount) { index ->
                    Rect(
                        bounds.x + PADDING + index % columns * slotSize,
                        bounds.y + PADDING + index / columns * slotSize,
                        slotSize,
                        slotSize,
                    )
                },
            )
        }

        private const val MAX_COLUMNS = 9
        private const val PADDING = 2
        private const val GAP = 2
        private val DROPDOWN_BORDER = 0xFF70777D.toInt()
        private val DROPDOWN_FILL = 0xF0181B1E.toInt()
    }
}

internal class ItemListTierDropdownState {
    private var representativeKey: ItemListEntryKey? = null
    private var renderedDropdown: RenderedDropdown? = null

    val isOpen: Boolean get() = representativeKey != null

    fun toggle(key: ItemListEntryKey) {
        representativeKey = key.takeUnless { it == representativeKey }
        renderedDropdown = null
    }

    fun render(
        context: GuiGraphicsExtractor,
        layout: ItemListLayout,
        entries: List<ItemListEntry>,
        drawTier: (Rect, ItemListEntry) -> Unit,
    ) {
        val key = representativeKey ?: return clear()
        val family = SkyBlockDataRepository.ItemListData.tierFamily(key) ?: return clear()
        val pageIndex = entries.indexOfFirst { it.key == key } - ItemListState.page * layout.pageSize
        if (pageIndex !in 0 until layout.pageSize) return clear()
        val current = ItemListTierDropdown.create(
            layout.panel,
            layout.slotBounds(pageIndex),
            family.tiers.size,
            layout.slotSize,
        )
        renderedDropdown = RenderedDropdown(current, family.tiers)
        current.renderBackground(context)
        family.tiers.forEachIndexed { index, tierKey ->
            SkyBlockDataRepository.entry(tierKey)?.let { drawTier(current.tierBounds[index], it) }
        }
    }

    fun keyAt(mouseX: Int, mouseY: Int): ItemListEntryKey? {
        val current = renderedDropdown ?: return null
        return current.layout.tierBounds.indexOfFirst { it.contains(mouseX, mouseY) }
            .takeIf { it >= 0 }
            ?.let(current.tierKeys::getOrNull)
    }

    fun clear() {
        representativeKey = null
        renderedDropdown = null
    }

    private data class RenderedDropdown(
        val layout: ItemListTierDropdown,
        val tierKeys: List<ItemListEntryKey>,
    )
}
