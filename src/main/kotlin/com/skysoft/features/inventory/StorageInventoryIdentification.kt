package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorage
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockOpenInventorySnapshot
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen

internal fun handleFor(screen: AbstractContainerScreen<*>?): StorageHandle? {
    if (screen == null || !HypixelLocationState.inSkyBlock || !isStorageOverlayEnabled) return null
    return storageHandleFor(screen)
}

private fun storageHandleFor(screen: AbstractContainerScreen<*>): StorageHandle? {
    if (screen !is ContainerScreen) return null
    return storageHandleFor(screen.title.cleanSkyBlockText(), screen.menu.rowCount)
}

internal fun storageHandleFor(snapshot: SkyBlockOpenInventorySnapshot): StorageHandle? {
    val rows = snapshot.menuRows ?: return null
    return storageHandleFor(snapshot.title, rows)
}

private fun storageHandleFor(title: String, rows: Int): StorageHandle? {
    if (title == "Storage") return StorageHandle.Overview
    val riftPageNumber = riftStorageTitlePattern.matchEntire(title)?.groupValues?.get(1)?.toIntOrNull()
    if (riftPageNumber != null) {
        return StorageHandle.Rift(riftStoragePageIndex(riftPageNumber - 1), rows - 1)
    }
    ToolkitType.fromTitle(title)?.let { return StorageHandle.Toolkit(it, rows) }
    return storagePageHandle(title, rows)
}

private fun storagePageHandle(title: String, rows: Int): StorageHandle.Page? {
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
