package com.skysoft.features.inventory

import com.skysoft.utils.gui.ScreenSlotLayout
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.Slot

private val storageSlotLayout = ScreenSlotLayout()

internal fun moveStorageOverlaySlot(screen: AbstractContainerScreen<*>, slot: Slot, x: Int, y: Int) {
    storageSlotLayout.move(screen, slot, x, y)
}

internal fun restoreStorageOverlaySlots(screen: AbstractContainerScreen<*>? = null) {
    storageSlotLayout.restore(screen)
}
