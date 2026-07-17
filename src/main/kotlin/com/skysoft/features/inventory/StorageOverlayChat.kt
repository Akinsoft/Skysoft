package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import com.skysoft.utils.ChangeResult
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility

internal fun registerStorageOverlayChat() {
    ChatEvents.onVisibleMessage { message ->
        if (isStorageOverlayEnabled && message.isSystemLike) recordBackpackRemoval(message.body)
        ChatMessageVisibility.SHOW
    }
}

internal fun recordBackpackRemoval(message: String): ChangeResult {
    val pageIndex = removedBackpackPageIndex(message) ?: return ChangeResult.UNCHANGED
    val backpackSlot = pageIndex - ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES + 1
    emptyOverviewStacks[pageIndex] = emptyBackpackShortcutStack(backpackSlot)
    val changed = storage.skyBlockStoragePages.remove(pageIndex) != null
    if (changed) {
        StorageSearchIndex.invalidatePages()
        ProfileStorageApi.markDirty()
    }
    return ChangeResult.from(changed)
}

internal fun removedBackpackPageIndex(message: String): Int? {
    val backpackSlot = removedBackpackPattern.matchEntire(message)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    if (backpackSlot !in 1..ProfileStorage.SKYBLOCK_STORAGE_BACKPACK_PAGES) return null
    return ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES + backpackSlot - 1
}

private val removedBackpackPattern = Regex("""^Removed backpack from slot ([0-9]+)!$""")
