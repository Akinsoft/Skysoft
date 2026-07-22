package com.skysoft.features.inventory

import com.skysoft.features.inventory.itemlist.ItemListController
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

object InventoryOverlayInput {
    @JvmStatic
    fun isPointCovered(
        screen: AbstractContainerScreen<*>,
        mouseX: Double,
        mouseY: Double,
    ): Boolean =
        ItemListController.isClickInside(screen, mouseX, mouseY) ||
            StorageOverlayController.isClickInsideOverlay(screen, mouseX, mouseY)
}
