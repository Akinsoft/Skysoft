package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorage

internal fun storageEntry(pageIndex: Int): ProfileStorage.SkyBlockStoragePageData? = when {
    isRiftStoragePage(pageIndex) -> storage.skyBlockRiftStoragePages[riftStoragePageNumber(pageIndex)]
    else -> ToolkitType.fromPageIndex(pageIndex)?.let { storage.skyBlockToolkits[it.storageKey] }
        ?: storage.skyBlockStoragePages[pageIndex]
}

internal fun storageEntryExists(pageIndex: Int): Boolean = when {
    isRiftStoragePage(pageIndex) -> riftStoragePageNumber(pageIndex) in storage.skyBlockRiftStoragePages
    else -> ToolkitType.fromPageIndex(pageIndex)?.let { it.storageKey in storage.skyBlockToolkits }
        ?: (pageIndex in storage.skyBlockStoragePages)
}

internal fun displayStorageEntries(activePage: Int?): List<Pair<Int, ProfileStorage.SkyBlockStoragePageData>> = buildList {
    if (activePage != null && isRiftStoragePage(activePage)) {
        storage.skyBlockRiftStoragePages.toSortedMap().forEach { (pageNumber, page) ->
            if (pageNumber in 0 until ProfileStorage.SKYBLOCK_RIFT_STORAGE_PAGE_COUNT) {
                add(riftStoragePageIndex(pageNumber) to page)
            }
        }
        return@buildList
    }
    storage.skyBlockStoragePages.toSortedMap().forEach { (pageIndex, page) ->
        if (pageIndex in 0 until ProfileStorage.SKYBLOCK_STORAGE_PAGE_COUNT) add(pageIndex to page)
    }
    ToolkitType.entries.forEach { type ->
        storage.skyBlockToolkits[type.storageKey]?.let { add(type.pageIndex to it) }
    }
}

internal fun isRiftStoragePage(pageIndex: Int): Boolean =
    pageIndex in RiftStorage.FIRST_PAGE_INDEX..RiftStorage.LAST_PAGE_INDEX

internal fun riftStoragePageIndex(pageNumber: Int): Int = RiftStorage.FIRST_PAGE_INDEX + pageNumber

internal fun riftStoragePageNumber(pageIndex: Int): Int = pageIndex - RiftStorage.FIRST_PAGE_INDEX

internal fun selectorPageIndices(activePage: Int?): IntRange =
    if (activePage != null && isRiftStoragePage(activePage)) {
        RiftStorage.FIRST_PAGE_INDEX..RiftStorage.LAST_PAGE_INDEX
    } else {
        0 until ProfileStorage.SKYBLOCK_STORAGE_PAGE_COUNT
    }

internal fun StorageHandle.isSelectorVisible(): Boolean = config.settings.miniMenu && this !is StorageHandle.Rift

internal fun StorageHandle.entryIndex(): Int? = when (this) {
    StorageHandle.Overview -> null
    is StorageHandle.Page -> pageIndex
    is StorageHandle.Rift -> pageIndex
    is StorageHandle.Toolkit -> type.pageIndex
}

internal fun StorageHandle.gridRows(): Int? = when (this) {
    StorageHandle.Overview -> null
    is StorageHandle.Page -> rows
    is StorageHandle.Rift -> rows
    is StorageHandle.Toolkit -> rows
}

internal fun StorageHandle.slotOffset(): Int = when (this) {
    StorageHandle.Overview -> 0
    is StorageHandle.Page -> StoragePages.COLUMNS
    is StorageHandle.Rift -> RiftStorage.SLOT_OFFSET
    is StorageHandle.Toolkit -> 0
}
