package com.skysoft.events.input

import com.skysoft.data.InteractionClick
import com.skysoft.utils.SkysoftErrorBoundary
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.world.item.ItemStack

class ItemUseEvent(
    val clickType: InteractionClick,
    val itemInHand: ItemStack?,
)

fun interface ItemUseCallback {
    fun shouldCancelItemUse(event: ItemUseEvent): Boolean
}

object ItemUseEvents {
    private val event: Event<ItemUseCallback> = EventFactory.createArrayBacked(ItemUseCallback::class.java) { listeners ->
        ItemUseCallback { event ->
            listeners.any { it.shouldCancelItemUse(event) }
        }
    }
    private var activePredicates: List<() -> Boolean> = emptyList()

    fun register(
        boundary: String,
        isActive: () -> Boolean,
        listener: ItemUseCallback,
    ) {
        activePredicates += isActive
        event.register { event ->
            isActive() && SkysoftErrorBoundary.value(boundary, false) { listener.shouldCancelItemUse(event) }
        }
    }

    fun hasActiveListeners(): Boolean = activePredicates.any { it() }

    fun shouldCancelItemUse(itemUse: ItemUseEvent): Boolean = event.invoker().shouldCancelItemUse(itemUse)
}
