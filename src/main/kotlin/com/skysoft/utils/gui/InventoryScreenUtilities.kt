package com.skysoft.utils.gui

import com.skysoft.mixin.AbstractContainerScreenAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

internal fun AbstractContainerScreen<*>.nonPlayerSlots(): List<Slot> {
    val playerInventory = Minecraft.getInstance().player?.inventory
    return menu.slots.filter { playerInventory == null || it.container !== playerInventory }
}

internal fun AbstractContainerScreen<*>.nonPlayerInventoryKey(titleText: String): String = buildString {
    append(menu.containerId).append('|').append(titleText).append('|')
    nonPlayerSlots().forEach { slot ->
        append(slot.containerSlot).append(':')
        val stack = slot.item
        if (!stack.isEmpty) append(ItemStack.hashItemAndComponents(stack)).append(':').append(stack.count)
        append(';')
    }
}

internal fun AbstractContainerScreen<*>.nonPlayerSlotAt(mouseX: Int, mouseY: Int, includeEmpty: Boolean = false): Slot? {
    val accessor = this as AbstractContainerScreenAccessor
    val left = accessor.skysoftGetLeftPos()
    val top = accessor.skysoftGetTopPos()
    return nonPlayerSlots().firstOrNull { slot ->
        (includeEmpty || !slot.item.isEmpty) &&
            mouseX in left + slot.x until left + slot.x + VANILLA_SLOT_SIZE &&
            mouseY in top + slot.y until top + slot.y + VANILLA_SLOT_SIZE
    }
}

internal const val VANILLA_SLOT_SIZE = 16
