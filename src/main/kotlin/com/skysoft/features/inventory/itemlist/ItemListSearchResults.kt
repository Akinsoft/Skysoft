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
        if (query.isBlank()) return emptyList()
        val currentKey = ItemFilterKey(
            query = query,
            showVanilla = showVanilla,
            snapshotVersion = SkyBlockDataRepository.snapshotVersion,
        )
        if (filterKey == currentKey) return filteredEntryCache
        return SkyBlockDataRepository.ItemListData.search(query).filter { entry ->
            entry.key.kind == ItemListEntryKind.SKYBLOCK ||
                entry.key.kind == ItemListEntryKind.ENTITY ||
                (entry.source == "minecraft" && showVanilla)
        }.also {
            filterKey = currentKey
            filteredEntryCache = it
        }
    }
}

private data class ItemFilterKey(
    val query: String,
    val showVanilla: Boolean,
    val snapshotVersion: Long,
)
