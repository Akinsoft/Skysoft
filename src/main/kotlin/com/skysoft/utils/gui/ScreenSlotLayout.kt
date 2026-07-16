package com.skysoft.utils.gui

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.Slot
import java.util.IdentityHashMap

internal class ScreenSlotLayout {
    private val originalPositions = IdentityHashMap<AbstractContainerScreen<*>, MutableMap<Slot, SlotPosition>>()

    fun move(screen: AbstractContainerScreen<*>, slot: Slot, x: Int, y: Int) {
        remember(screen, slot)
        slot.x = x
        slot.y = y
    }

    fun moveFromOriginal(screen: AbstractContainerScreen<*>, slot: Slot, deltaX: Int = 0, deltaY: Int = 0) {
        val position = remember(screen, slot)
        slot.x = position.x + deltaX
        slot.y = position.y + deltaY
    }

    fun hasSnapshot(screen: AbstractContainerScreen<*>): Boolean = screen in originalPositions

    fun restore(screen: AbstractContainerScreen<*>? = null) {
        if (screen != null) {
            val slots = originalPositions.remove(screen) ?: return
            restoreSlots(slots)
            return
        }

        val slotsByScreen = originalPositions.values.toList()
        originalPositions.clear()
        slotsByScreen.forEach(::restoreSlots)
    }

    private fun remember(screen: AbstractContainerScreen<*>, slot: Slot): SlotPosition =
        originalPositions.getOrPut(screen) { IdentityHashMap() }
            .getOrPut(slot) { SlotPosition(slot.x, slot.y) }

    private fun restoreSlots(slots: Map<Slot, SlotPosition>) {
        slots.forEach { (slot, position) ->
            slot.x = position.x
            slot.y = position.y
        }
    }
}

private data class SlotPosition(val x: Int, val y: Int)
