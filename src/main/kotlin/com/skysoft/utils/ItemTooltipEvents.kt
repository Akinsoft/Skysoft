package com.skysoft.utils

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag

internal object ItemTooltipEvents {
    private val listeners =
        ActiveListenerRegistry<(ItemStack, Item.TooltipContext, TooltipFlag, MutableList<Component>) -> Unit>()
    private var registered = false

    fun register(
        boundary: String,
        isActive: () -> Boolean,
        listener: (ItemStack, Item.TooltipContext, TooltipFlag, MutableList<Component>) -> Unit,
    ) {
        listeners.register(boundary, isActive, listener)
        if (registered) return
        registered = true
        ItemTooltipCallback.EVENT.register { stack, context, flag, tooltip ->
            listeners.forEachActive { callback -> callback(stack, context, flag, tooltip) }
        }
    }
}
