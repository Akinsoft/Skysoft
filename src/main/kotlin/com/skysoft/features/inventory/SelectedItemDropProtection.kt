package com.skysoft.features.inventory

import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.player.LocalPlayer

object SelectedItemDropProtection {
    @JvmStatic
    fun shouldBlock(player: LocalPlayer): Boolean {
        if (ItemProtectionManager.shouldAllowDungeonUltimate(player)) return false
        val slotLockResult = SlotLockManager.handleSelectedItemDrop(player)
        val itemProtectionResult = ItemProtectionManager.handleWorldDrop(player)
        return slotLockResult == InputHandlingResult.CONSUMED || itemProtectionResult == InputHandlingResult.CONSUMED
    }
}
