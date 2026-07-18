package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorage
import java.util.IdentityHashMap
import net.minecraft.world.item.ItemStack

internal object StorageSearchIndex {
    private var indexedQuery = ""
    private var searchQuery = InventoryItemSearchQuery.EMPTY
    private val itemTextByEncodedStack = mutableMapOf<String, String>()
    private val pageMatches = IdentityHashMap<ProfileStorage.SkyBlockStoragePageData, Boolean>()

    val hasQuery: Boolean
        get() = query().hasTerms

    fun matches(page: ProfileStorage.SkyBlockStoragePageData): Boolean {
        val currentQuery = query()
        if (!currentQuery.hasTerms) return true
        return pageMatches.getOrPut(page) {
            page.items.any { item -> matches(item, currentQuery) }
        }
    }

    fun matches(item: ProfileStorage.SkyBlockStorageItemData?): Boolean {
        val currentQuery = query()
        return !currentQuery.hasTerms || matches(item, currentQuery)
    }

    fun matches(stack: ItemStack): Boolean {
        val currentQuery = query()
        return !currentQuery.hasTerms || currentQuery.matches(stack)
    }

    fun invalidatePages() {
        pageMatches.clear()
    }

    fun clear() {
        indexedQuery = ""
        searchQuery = InventoryItemSearchQuery.EMPTY
        itemTextByEncodedStack.clear()
        pageMatches.clear()
    }

    private fun query(): InventoryItemSearchQuery {
        if (storageSearchField.text == indexedQuery) return searchQuery
        indexedQuery = storageSearchField.text
        searchQuery = InventoryItemSearchQuery.from(storageSearchField.text)
        pageMatches.clear()
        return searchQuery
    }

    private fun matches(item: ProfileStorage.SkyBlockStorageItemData?, query: InventoryItemSearchQuery): Boolean {
        val encoded = item?.encodedStack?.takeIf { it.isNotBlank() } ?: return false
        val searchableText = itemTextByEncodedStack.getOrPut(encoded) { InventoryItemSearchIndex.text(stackFor(item)) }
        return query.matchesSearchableText(searchableText)
    }
}
