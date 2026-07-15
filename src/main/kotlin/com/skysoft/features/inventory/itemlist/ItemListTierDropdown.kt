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
    companion object {
        fun create(panel: Rect, anchor: Rect, tierCount: Int, requestedSlotSize: Int): ItemListTierDropdown {
            require(tierCount > 0)
            require(requestedSlotSize > 0)
            var columns = 1
            var slotSize = 0
            for (candidateColumns in 1..tierCount.coerceAtMost(MAX_COLUMNS)) {
                val candidateRows = (tierCount + candidateColumns - 1) / candidateColumns
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
            val rows = (tierCount + columns - 1) / columns
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
    }
}

internal class ItemListTierDropdownState {
    private var representativeKey: ItemListEntryKey? = null
    private var dropdown: ItemListTierDropdown? = null
    private var tierKeys: List<ItemListEntryKey> = emptyList()

    val isOpen: Boolean get() = representativeKey != null

    fun toggle(key: ItemListEntryKey) {
        representativeKey = key.takeUnless { it == representativeKey }
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
        dropdown = current
        tierKeys = family.tiers
        context.fill(
            current.bounds.x,
            current.bounds.y,
            current.bounds.x + current.bounds.width,
            current.bounds.y + current.bounds.height,
            DROPDOWN_BORDER,
        )
        context.fill(
            current.bounds.x + 1,
            current.bounds.y + 1,
            current.bounds.x + current.bounds.width - 1,
            current.bounds.y + current.bounds.height - 1,
            DROPDOWN_FILL,
        )
        family.tiers.forEachIndexed { index, tierKey ->
            SkyBlockDataRepository.entry(tierKey)?.let { drawTier(current.tierBounds[index], it) }
        }
    }

    fun keyAt(mouseX: Int, mouseY: Int): ItemListEntryKey? {
        val current = dropdown ?: return null
        return current.tierBounds.indexOfFirst { it.contains(mouseX, mouseY) }
            .takeIf { it >= 0 }
            ?.let(tierKeys::getOrNull)
    }

    fun clear() {
        representativeKey = null
        dropdown = null
        tierKeys = emptyList()
    }

    private companion object {
        val DROPDOWN_BORDER = 0xFF70777D.toInt()
        val DROPDOWN_FILL = 0xF0181B1E.toInt()
    }
}
