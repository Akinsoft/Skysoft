package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.SkyBlockOpenInventoryApi
import com.skysoft.data.skyblock.SkyBlockOpenInventorySnapshot
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.ConsumerActivity
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen

internal object StorageCache {
    private val consumers = ActiveConsumerRegistry()

    fun register() {
        ProfileStorageApi.registerConsumer("Storage Cache") { consumers.hasActiveConsumers }
        SkyBlockProfileApi.onProfileChange(
            "Storage Cache profile reset",
            { consumers.hasActiveConsumers },
            { resetCacheTransientState() },
        )
        SkysoftClientEvents.onDisconnect("Storage Cache disconnect reset", ::resetCacheTransientState)
        SkyBlockOpenInventoryApi.onChange(
            "Storage Cache inventory",
            isActive = { consumers.hasActiveConsumers },
            listener = ::updateCurrentSnapshot,
        )
        SkysoftClientEvents.onEndTick(
            "Storage Cache activity",
            isActive = { consumers.isActiveOrDeactivating },
        ) {
            if (consumers.activity() == ConsumerActivity.DEACTIVATED) resetCacheTransientState()
        }
    }

    fun registerConsumer(id: String, isActive: () -> Boolean) {
        consumers.register(id, isActive)
    }

    private fun updateCurrentSnapshot(snapshot: SkyBlockOpenInventorySnapshot?) {
        val handle = snapshot?.let(::storageHandleFor) ?: run {
            lastInventoryKey = null
            return
        }
        if (!isStorageOverlayEnabled && handle != StorageHandle.Overview && handle !is StorageHandle.Page) {
            lastInventoryKey = null
            return
        }
        readSnapshot(snapshot, handle)
    }

    private fun resetCacheTransientState() {
        lastInventoryKey = null
        decodedStacks.clear()
        emptyOverviewStacks.clear()
        StorageSearchIndex.clear()
    }
}

internal fun storageHandleFor(screen: AbstractContainerScreen<*>): StorageHandle? {
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
