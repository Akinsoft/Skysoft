package com.skysoft.features.inventory.itemlist

import com.skysoft.data.skyblock.ItemListEntry
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.SkyBlockDataLoadState
import com.skysoft.data.skyblock.SkyBlockDataRepository

internal class ItemListSearchResults {
    private var filterKey: ItemFilterKey? = null
    private var filteredEntryCache: List<ItemListEntry> = emptyList()

    fun entries(search: String, showVanilla: Boolean): List<ItemListEntry> {
        val status = SkyBlockDataRepository.status
        if (status.state != SkyBlockDataLoadState.READY) return emptyList()
        val query = search.trim()
        val filter = itemListTypeFilter()
        val order = itemListOrder()
        if (query.isBlank() && filter == ItemListTypeFilter.ALL) return emptyList()
        val descending = itemListOrderIsDescending(order)
        val currentKey = ItemFilterKey(
            query = query,
            showVanilla = showVanilla,
            snapshotVersion = SkyBlockDataRepository.snapshotVersion,
            filter = filter,
            order = order,
            descending = descending,
            priceVersion = if (order == ItemListOrder.PRICE) itemListPriceVersion() else 0L,
        )
        if (filterKey == currentKey) return filteredEntryCache
        val filtered = SkyBlockDataRepository.ItemListData.search(query).filter { entry ->
            entry.isListed(showVanilla) && filter.matches(entry)
        }
        val ordered = if (order == ItemListOrder.RELEVANCE) {
            filtered
        } else {
            filtered.sortedWith { left, right -> compareItemListEntries(left, right, order, descending) }
        }
        filterKey = currentKey
        filteredEntryCache = ordered
        return ordered
    }
}

private fun ItemListEntry.isListed(showVanilla: Boolean): Boolean =
    key.kind == ItemListEntryKind.SKYBLOCK ||
        key.kind == ItemListEntryKind.ENTITY ||
        (source == "minecraft" && showVanilla)

private data class ItemFilterKey(
    val query: String,
    val showVanilla: Boolean,
    val snapshotVersion: Long,
    val filter: ItemListTypeFilter,
    val order: ItemListOrder,
    val descending: Boolean,
    val priceVersion: Long,
)
