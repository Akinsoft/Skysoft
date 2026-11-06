package com.skysoft.features.helditem

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object HeldItemCustomization {
    @JvmStatic
    fun isEligible(itemStack: ItemStack): Boolean = itemStack.item != Items.MAP && itemStack.item != Items.FILLED_MAP
}
