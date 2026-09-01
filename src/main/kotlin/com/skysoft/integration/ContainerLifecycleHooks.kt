package com.skysoft.integration

import com.skysoft.data.skyblock.SkyBlockOpenInventoryApi
import com.skysoft.features.bazaar.BazaarTracker
import com.skysoft.features.inventory.ExperimentationTableHelper
import com.skysoft.features.inventory.InventoryEquipment
import com.skysoft.features.inventory.ItemProtectionManager
import com.skysoft.features.inventory.SlotLockManager
import com.skysoft.features.inventory.StorageOverlayController
import com.skysoft.gui.scale.InventoryCursorMemory
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

object ContainerLifecycleHooks {
    @JvmStatic
    fun layout(screen: AbstractContainerScreen<*>) {
        SkysoftErrorBoundary.run("SkyBlock open inventory capture") {
            SkyBlockOpenInventoryApi.capture(screen)
        }
        SkysoftErrorBoundary.run("Storage Overlay layout") {
            StorageOverlayController.layoutScreen(screen)
        }
        SkysoftErrorBoundary.run("Inventory Equipment layout") {
            InventoryEquipment.layoutScreen(screen)
        }
    }

    @JvmStatic
    fun removed(screen: AbstractContainerScreen<*>) {
        SkysoftErrorBoundary.run("Inventory cursor screen removal") {
            InventoryCursorMemory.prepareForMouseGrab()
        }
        SkysoftErrorBoundary.run("Bazaar Tracker screen cleanup") {
            BazaarTracker.restoreOrderMenu(screen)
        }
        SkysoftErrorBoundary.run("Inventory Equipment screen cleanup") {
            InventoryEquipment.restoreScreen(screen)
        }
        SkysoftErrorBoundary.run("Slot Lock screen cleanup") {
            SlotLockManager.clearInputState()
        }
        SkysoftErrorBoundary.run("Item Protection screen cleanup") {
            ItemProtectionManager.clearInputState()
        }
        SkysoftErrorBoundary.run("Experimentation Table helper cleanup") {
            ExperimentationTableHelper.onScreenRemoved(screen)
        }
    }
}
