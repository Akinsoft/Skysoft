package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorage
import com.skysoft.data.hypixel.HypixelLocationState
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

internal fun handleFor(screen: AbstractContainerScreen<*>?): StorageHandle? {
    if (screen == null || !HypixelLocationState.inSkyBlock || !isStorageOverlayEnabled) return null
    return storageHandleFor(screen)
}

internal fun storagePageHandle(title: String, rows: Int): StorageHandle.Page? {
    val enderChestPage = enderChestTitlePattern.matchEntire(title)?.groupValues?.get(1)?.toIntOrNull()
    if (enderChestPage != null && enderChestPage in 1..ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES) {
        return StorageHandle.Page(enderChestPage - 1, rows - 1)
    }
    val backpackPage = backpackTitlePattern.matchEntire(title)?.groupValues?.get(1)?.toIntOrNull()
    return if (backpackPage != null && backpackPage in 1..ProfileStorage.SKYBLOCK_STORAGE_BACKPACK_PAGES) {
        StorageHandle.Page(ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES + backpackPage - 1, rows - 1)
    } else {
        null
    }
}
