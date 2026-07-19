package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen

internal object StorageCache {
    private val consumers = ActiveConsumerRegistry()
    private var wasActive = false

    fun register() {
        ProfileStorageApi.registerConsumer("Storage Cache") { consumers.hasActiveConsumers }
        SkyBlockProfileApi.onProfileChange(
            "Storage Cache profile reset",
            { consumers.hasActiveConsumers },
            { resetCacheTransientState() },
        )
        SkysoftClientEvents.onDisconnect("Storage Cache disconnect reset", ::resetCacheTransientState)
        SkysoftClientEvents.onEndTick(
            "Storage Cache tick",
            isActive = { consumers.hasActiveConsumers || wasActive },
        ) {
            val isActive = consumers.hasActiveConsumers
            if (!isActive) {
                if (wasActive) resetCacheTransientState()
                wasActive = false
                return@onEndTick
            }
            wasActive = true
            updateCurrentScreen()
        }
    }

    fun registerConsumer(id: String, isActive: () -> Boolean) {
        consumers.register(id, isActive)
    }

    private fun updateCurrentScreen() {
        if (!HypixelLocationState.inSkyBlock) {
            lastInventoryKey = null
            return
        }
        val screen = MinecraftClient.screen() as? AbstractContainerScreen<*> ?: run {
            lastInventoryKey = null
            return
        }
        val handle = storageHandleFor(screen) ?: run {
            lastInventoryKey = null
            return
        }
        if (!isStorageOverlayEnabled && handle != StorageHandle.Overview && handle !is StorageHandle.Page) {
            lastInventoryKey = null
            return
        }
        readScreen(screen, handle)
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
    val menu = screen.menu
    val title = screen.title.cleanSkyBlockText()
    if (title == "Storage") return StorageHandle.Overview
    val riftPageNumber = riftStorageTitlePattern.matchEntire(title)?.groupValues?.get(1)?.toIntOrNull()
    if (riftPageNumber != null) {
        return StorageHandle.Rift(riftStoragePageIndex(riftPageNumber - 1), menu.rowCount - 1)
    }
    ToolkitType.fromTitle(title)?.let { return StorageHandle.Toolkit(it, menu.rowCount) }
    return storagePageHandle(title, menu.rowCount)
}
