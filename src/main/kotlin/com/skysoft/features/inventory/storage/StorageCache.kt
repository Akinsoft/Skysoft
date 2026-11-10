package com.skysoft.features.inventory.storage

import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.SkyBlockOpenInventoryApi
import com.skysoft.data.skyblock.SkyBlockOpenInventorySnapshot
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.ConsumerActivity
import com.skysoft.utils.SkysoftClientEvents

internal object StorageCache {
    private val consumers = ActiveConsumerRegistry()
    private var lastInventoryKey: String? = null

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

    fun invalidateSnapshot() {
        lastInventoryKey = null
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
        if (snapshot.key == lastInventoryKey) return
        lastInventoryKey = snapshot.key
        readSnapshot(snapshot, handle)
    }

    private fun resetCacheTransientState() {
        lastInventoryKey = null
        StorageItemStacks.clear()
        StorageSearchIndex.clear()
    }
}
