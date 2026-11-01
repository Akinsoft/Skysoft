package com.skysoft.integration

import com.skysoft.data.skyblock.AttributeShardItemReader
import com.skysoft.data.skyblock.AttributeShardTransfers
import com.skysoft.data.skyblock.SkyBlockDroppedItems
import com.skysoft.data.skyblock.SkyBlockSackContents
import com.skysoft.data.skyblock.SkyBlockSackTransfers
import com.skysoft.utils.MinecraftClient
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerInput

object ContainerTransferHooks {
    @JvmStatic
    fun trackContainerInput(containerId: Int, slotNum: Int, buttonNum: Int, input: ContainerInput, player: Player) {
        val menu = player.containerMenu
        if (menu.containerId != containerId) return
        val title = (MinecraftClient.screen() as? AbstractContainerScreen<*>)?.takeIf { it.menu === menu }?.title?.string
        val slot = menu.slots.getOrNull(slotNum)
        if (title == AttributeShardItemReader.HUNTING_BOX && buttonNum == 1 && slot != null) {
            AttributeShardTransfers.recordRemoval(slot.item)
        }
        if (title == SkyBlockSackContents.SACK_OF_SACKS_MENU && slot?.item?.hoverName?.string == INSERT_INVENTORY_NAME) {
            SkyBlockSackTransfers.recordInsertInventory()
        }
        when {
            input == ContainerInput.THROW && slot != null ->
                SkyBlockDroppedItems.recordIntent(slot.item, if (buttonNum == 0) 1 else slot.item.count)
            input == ContainerInput.PICKUP && slotNum == OUTSIDE_SLOT ->
                SkyBlockDroppedItems.recordIntent(menu.carried, if (buttonNum == 0) menu.carried.count else 1)
        }
    }

    private const val OUTSIDE_SLOT = -999
    private const val INSERT_INVENTORY_NAME = "Insert inventory"
}
