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

internal fun AbstractContainerScreen<*>.nonPlayerInventoryItems(): Map<Int, ItemStack> =
    nonPlayerSlots()
        .mapNotNull { slot -> slot.item.takeUnless { it.isEmpty }?.let { slot.containerSlot to it } }
        .toMap()

internal fun AbstractContainerScreen<*>.nonPlayerInventoryKey(
    titleText: String = title.string,
    stackSignature: (ItemStack) -> String = ::defaultStackSignature,
): String = buildString {
    append(menu.containerId).append('|').append(titleText).append('|')
    nonPlayerSlots().forEach { slot ->
        append(slot.containerSlot).append(':')
        val signature = stackSignature(slot.item)
        if (signature.isNotEmpty()) append(signature)
        append(';')
    }
}

private fun defaultStackSignature(stack: ItemStack): String =
    if (stack.isEmpty) "" else "${ItemStack.hashItemAndComponents(stack)}:${stack.count}"

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
