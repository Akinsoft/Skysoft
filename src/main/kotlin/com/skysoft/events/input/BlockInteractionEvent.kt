package com.skysoft.events.input

import com.skysoft.data.InteractionClick
import com.skysoft.events.ActiveEvents
import com.skysoft.utils.WorldVec
import net.minecraft.world.item.ItemStack

class BlockInteractionEvent(
    val clickType: InteractionClick,
    val itemInHand: ItemStack?,
    val position: WorldVec,
)

internal object BlockInteractionEvents : ActiveEvents<(BlockInteractionEvent) -> Boolean>() {
    fun shouldCancelBlockClick(blockClick: BlockInteractionEvent): Boolean =
        listeners.anyActive { listener -> listener(blockClick) }
}
