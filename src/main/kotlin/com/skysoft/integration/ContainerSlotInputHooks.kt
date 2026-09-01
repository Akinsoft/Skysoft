package com.skysoft.integration

import com.skysoft.features.bazaar.BazaarTracker
import com.skysoft.features.inventory.AnimatedDyeArmorCache
import com.skysoft.features.inventory.ExperimentationTableHelper
import com.skysoft.features.inventory.InventoryDropSelectionGuard
import com.skysoft.features.inventory.ItemProtectionManager
import com.skysoft.features.inventory.SkyBlockMenuInventoryDropFix
import com.skysoft.features.inventory.SlotBindingManager
import com.skysoft.features.inventory.SlotLockManager
import com.skysoft.features.pets.PetStorageService
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot

object ContainerSlotInputHooks {
    @JvmStatic
    fun didConsumeSlotClick(
        screen: AbstractContainerScreen<*>,
        slot: Slot,
        slotId: Int,
        button: Int,
        action: ContainerInput,
    ): Boolean {
        SkysoftErrorBoundary.run("Animated dye armor Wardrobe selection") {
            AnimatedDyeArmorCache.observeWardrobeSelection(screen, slot, button, action)
        }
        if (didConsume("Item Protection slot click") {
                ItemProtectionManager.handleSlotClick(screen, slot, slotId, action)
            }
        ) return true
        if (didConsume("Slot Binding slot click") {
                SlotBindingManager.handleSlotClick(screen, slot, action).also { result ->
                    if (result == InputHandlingResult.CONSUMED) {
                        SkysoftErrorBoundary.run("Pet Storage slot click") {
                            PetStorageService.onSlotClick(slot, slotId, button)
                        }
                    }
                }
            }
        ) return true
        if (didConsume("Slot Lock slot click") {
                SlotLockManager.handleSlotClick(screen, slot, button, action)
            }
        ) return true
        SkysoftErrorBoundary.run("Experimentation Table helper slot click") {
            ExperimentationTableHelper.onSlotClick(screen, slot, action)
        }
        SkysoftErrorBoundary.run("Pet Storage slot click") {
            PetStorageService.onSlotClick(slot, slotId, button)
        }
        return false
    }

    @JvmStatic
    fun canQuickCraftInto(slot: Slot): Boolean = SlotLockManager.canQuickCraftInto(slot)

    @JvmStatic
    fun handleContainerInput(
        screen: AbstractContainerScreen<*>,
        slotId: Int,
        action: ContainerInput,
        player: Player,
        original: Runnable,
    ) {
        if (BazaarTracker.shouldBlockOrderInteraction(screen, slotId)) return
        val guard: InventoryDropSelectionGuard? = SkyBlockMenuInventoryDropFix.beginContainerThrow(player, slotId, action)
        try {
            original.run()
        } finally {
            SkyBlockMenuInventoryDropFix.finishContainerThrow(guard)
        }
    }

    private fun didConsume(boundary: String, action: () -> InputHandlingResult): Boolean =
        SkysoftErrorBoundary.value(boundary, false) { action() == InputHandlingResult.CONSUMED }
}
