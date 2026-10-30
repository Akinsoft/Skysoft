package com.skysoft.events.input

import com.skysoft.data.InteractionClick
import com.skysoft.events.ActiveEvents
import net.minecraft.world.item.ItemStack

class ItemUseEvent(
    val clickType: InteractionClick,
    val itemInHand: ItemStack?,
)

internal object ItemUseEvents : ActiveEvents<(ItemUseEvent) -> Boolean>() {
    fun shouldCancelItemUse(itemUse: ItemUseEvent): Boolean =
        listeners.anyActive { listener -> listener(itemUse) }
}
