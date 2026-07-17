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

    fun register(boundary: String, listener: ItemUseCallback) {
        event.register { event ->
            SkysoftErrorBoundary.value(boundary, false) { listener.shouldCancelItemUse(event) }
        }
    }

    fun shouldCancelItemUse(itemUse: ItemUseEvent): Boolean = event.invoker().shouldCancelItemUse(itemUse)
}
