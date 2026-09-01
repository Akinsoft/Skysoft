package com.skysoft.events.input

import com.skysoft.data.InteractionClick
import com.skysoft.utils.ActiveListenerRegistry
import net.minecraft.world.item.ItemStack

class ItemUseEvent(
    val clickType: InteractionClick,
    val itemInHand: ItemStack?,
)

fun interface ItemUseCallback {
    fun shouldCancelItemUse(event: ItemUseEvent): Boolean
}

object ItemUseEvents {
    private val listeners = ActiveListenerRegistry<ItemUseCallback>()

    fun register(
        boundary: String,
        isActive: () -> Boolean,
        listener: ItemUseCallback,
    ) {
        listeners.register(boundary, isActive, listener)
    }

    fun hasActiveListeners(): Boolean = listeners.hasActiveListeners

    fun shouldCancelItemUse(itemUse: ItemUseEvent): Boolean =
        listeners.anyActive { listener -> listener.shouldCancelItemUse(itemUse) }
}
