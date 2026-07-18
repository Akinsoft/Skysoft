package com.skysoft.events.input

import com.skysoft.data.InteractionClick
import com.skysoft.utils.WorldVec
import com.skysoft.utils.SkysoftErrorBoundary
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
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
    private val event: Event<BlockClickCallback> = EventFactory.createArrayBacked(BlockClickCallback::class.java) { listeners ->
        BlockClickCallback { event ->
            listeners.any { it.shouldCancelBlockClick(event) }
        }
    }
    private var activePredicates: List<() -> Boolean> = emptyList()

    fun register(
        boundary: String,
        isActive: () -> Boolean,
        listener: BlockClickCallback,
    ) {
        activePredicates += isActive
        event.register { event ->
            isActive() && SkysoftErrorBoundary.value(boundary, false) { listener.shouldCancelBlockClick(event) }
        }
    }

    fun hasActiveListeners(): Boolean = activePredicates.any { it() }

    fun shouldCancelBlockClick(blockClick: BlockInteractionEvent): Boolean = event.invoker().shouldCancelBlockClick(blockClick)
}
