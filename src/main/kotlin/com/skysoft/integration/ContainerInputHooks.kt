package com.skysoft.integration

import com.skysoft.features.bazaar.BazaarTracker
import com.skysoft.features.inventory.InventoryButtonManager
import com.skysoft.features.inventory.InventoryEquipment
import com.skysoft.features.inventory.InventoryOverlayInput
import com.skysoft.features.inventory.ItemProtectionManager
import com.skysoft.features.inventory.SlotLockManager
import com.skysoft.features.inventory.StorageOverlayController
import com.skysoft.features.inventory.itemlist.ItemListController
import com.skysoft.features.inventory.sacks.SackHudInput
import com.skysoft.features.profit.ProfitTrackerHudInput
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

object ContainerInputHooks {
    @JvmStatic
    fun didConsumeMouseClick(screen: AbstractContainerScreen<*>, click: MouseButtonEvent, doubled: Boolean): Boolean =
        didConsume("Item List mouse input") { ItemListController.handleMouseClick(screen, click, doubled) } ||
            didConsume("Storage Overlay mouse input") { StorageOverlayController.handleMouseClick(screen, click) } ||
            didConsume("Bazaar Tracker mouse input") { BazaarTracker.handleMouseClick(screen, click) } ||
            didConsume("Inventory Equipment mouse input") { InventoryEquipment.handleMouseClick(screen, click) } ||
            didConsume("Inventory Button mouse input") { InventoryButtonManager.handleMouseClick(screen, click) }

    @JvmStatic
    fun didConsumeMouseRelease(screen: AbstractContainerScreen<*>, click: MouseButtonEvent): Boolean =
        didConsume("Storage Overlay mouse release") { StorageOverlayController.handleMouseRelease(click) } ||
            didConsume("Inventory Button mouse release") { InventoryButtonManager.handleMouseRelease(screen, click) }

    @JvmStatic
    fun didConsumeMouseDrag(
        screen: AbstractContainerScreen<*>,
        click: MouseButtonEvent,
    ): Boolean = didConsume("Storage Overlay mouse drag") {
        StorageOverlayController.handleMouseDrag(screen, click)
    }

    @JvmStatic
    fun didConsumeMouseScroll(
        screen: AbstractContainerScreen<*>,
        mouseX: Double,
        mouseY: Double,
        amount: Double,
    ): Boolean =
        didConsume("Item List mouse scroll") {
            ItemListController.handleMouseScroll(screen, mouseX, mouseY, amount)
        } || didConsume("Storage Overlay mouse scroll") {
            StorageOverlayController.handleMouseScroll(screen, mouseX, mouseY, amount)
        }

    @JvmStatic
    fun didConsumeKeyPress(screen: AbstractContainerScreen<*>, event: KeyEvent): Boolean {
        if (didConsume("Sacks Tracker key input") { SackHudInput.handleKeyPress(event) }) return true
        if (didConsume("Profit Tracker key input") { ProfitTrackerHudInput.handleKeyPress(event) }) return true
        if (didConsume("Storage Overlay key input") { StorageOverlayController.handleKeyPress(screen, event) }) return true
        if (didConsume("Item List key input") { ItemListController.handleKeyPress(screen, event) }) return true
        val slotLockConsumed = didConsume("Slot Lock key input") { SlotLockManager.handleKeyPress(screen, event) }
        val itemProtectionConsumed = didConsume("Item Protection key input") {
            ItemProtectionManager.handleKeyPress(screen, event)
        }
        return slotLockConsumed || itemProtectionConsumed
    }

    @JvmStatic
    fun didConsumeCharacterInput(screen: AbstractContainerScreen<*>, event: CharacterEvent): Boolean {
        if (didConsume("Sacks Tracker character input") { SackHudInput.handleCharTyped(event) }) return true
        val profitTrackerConsumed = didConsume("Profit Tracker character input") {
            ProfitTrackerHudInput.handleCharTyped(event)
        }
        val itemListConsumed = didConsume("Item List character input") {
            ItemListController.handleCharTyped(screen, event)
        }
        val storageOverlayConsumed = didConsume("Storage Overlay character input") {
            StorageOverlayController.handleCharTyped(screen, event)
        }
        return profitTrackerConsumed || itemListConsumed || storageOverlayConsumed
    }

    @JvmStatic
    fun shouldSuppressScreenWidgets(screen: AbstractContainerScreen<*>): Boolean = StorageOverlayController.isActive(screen)

    @JvmStatic
    fun isPointCovered(screen: AbstractContainerScreen<*>, mouseX: Double, mouseY: Double): Boolean =
        InventoryOverlayInput.isPointCovered(screen, mouseX, mouseY)

    private fun didConsume(boundary: String, action: () -> InputHandlingResult): Boolean =
        SkysoftErrorBoundary.value(boundary, false) { action() == InputHandlingResult.CONSUMED }
}
