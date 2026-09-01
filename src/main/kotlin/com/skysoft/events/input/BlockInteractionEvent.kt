package com.skysoft.events.input

import com.skysoft.data.InteractionClick
import com.skysoft.utils.ActiveListenerRegistry
import com.skysoft.utils.WorldVec
import net.minecraft.world.item.ItemStack

class BlockInteractionEvent(
    val clickType: InteractionClick,
    val itemInHand: ItemStack?,
    val position: WorldVec,
)

fun interface BlockClickCallback {
    fun shouldCancelBlockClick(event: BlockInteractionEvent): Boolean
}

object BlockInteractionEvents {
    private val listeners = ActiveListenerRegistry<BlockClickCallback>()

    fun register(
        boundary: String,
        isActive: () -> Boolean,
        listener: BlockClickCallback,
    ) {
        listeners.register(boundary, isActive, listener)
    }

    fun hasActiveListeners(): Boolean = listeners.hasActiveListeners

    fun shouldCancelBlockClick(blockClick: BlockInteractionEvent): Boolean =
        listeners.anyActive { listener -> listener.shouldCancelBlockClick(blockClick) }
}
