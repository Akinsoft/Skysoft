package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility

internal fun registerStorageOverlayChat() {
    ChatEvents.onVisibleMessage("Storage Overlay chat", { isStorageOverlayEnabled }) { message ->
        if (isStorageOverlayEnabled && message.isSystemLike) recordBackpackRemoval(message.body)
        ChatMessageVisibility.SHOW
    }
}

private fun recordBackpackRemoval(message: String) {
    val pageIndex = removedBackpackPageIndex(message) ?: return
    val backpackSlot = pageIndex - ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES + 1
    StorageItemStacks.rememberOverviewPlaceholder(pageIndex, emptyBackpackShortcutStack(backpackSlot))
    if (pageIndex in storage.skyBlockStoragePages) {
        StorageSearchIndex.invalidatePages()
        ProfileStorageApi.updateProfile { it.skyBlockStoragePages.remove(pageIndex) }
    }
}

internal fun removedBackpackPageIndex(message: String): Int? {
    val backpackSlot = removedBackpackPattern.matchEntire(message)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    if (backpackSlot !in 1..ProfileStorage.SKYBLOCK_STORAGE_BACKPACK_PAGES) return null
    return ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES + backpackSlot - 1
}

private val removedBackpackPattern = Regex("""^Removed backpack from slot ([0-9]+)!$""")
