package com.skysoft.features.inventory

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockMenuItem.isSkyBlockMenu
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack

object SkyBlockMenuInventoryDropFix {
    fun beginContainerThrow(
        player: Player,
        slotId: Int,
        action: ContainerInput,
    ): InventoryDropSelectionGuard? {
        if (action != ContainerInput.THROW) return null
        val localPlayer = player as? LocalPlayer ?: return null
        val sourceItem = localPlayer.containerMenu.slots.getOrNull(slotId)?.item
        if (!shouldRedirectThrow(localPlayer, sourceItem)) return null

        val originalSlot = localPlayer.inventory.selectedSlot
        val temporarySlot = temporaryHotbarSlot(localPlayer, originalSlot) ?: return null
        localPlayer.connection.send(ServerboundSetCarriedItemPacket(temporarySlot))
        return InventoryDropSelectionGuard(localPlayer, originalSlot)
    }

    fun finishContainerThrow(guard: InventoryDropSelectionGuard?) {
        guard ?: return
        guard.player.connection.send(ServerboundSetCarriedItemPacket(guard.originalSlot))
    }

    private fun temporaryHotbarSlot(player: LocalPlayer, originalSlot: Int): Int? {
        val inventory = player.inventory
        var firstNonMenuSlot: Int? = null
        for (offset in 1 until Inventory.SELECTION_SIZE) {
            val candidate = (originalSlot + offset) % Inventory.SELECTION_SIZE
            val item = inventory.getItem(candidate)
            if (item.isEmpty) return candidate
            if (firstNonMenuSlot == null && !item.isSkyBlockMenu()) firstNonMenuSlot = candidate
        }
        return firstNonMenuSlot
    }

    private fun shouldRedirectThrow(player: LocalPlayer, sourceItem: ItemStack?): Boolean =
        config.preventSkyBlockMenuOpeningOnInventoryDrop &&
            HypixelLocationState.inSkyBlock &&
            player.mainHandItem.isSkyBlockMenu() &&
            sourceItem != null &&
            !sourceItem.isEmpty &&
            !sourceItem.isSkyBlockMenu()

    private val config
        get() = SkysoftConfigGui.config().fixes
}

class InventoryDropSelectionGuard internal constructor(
    internal val player: LocalPlayer,
    internal val originalSlot: Int,
)
