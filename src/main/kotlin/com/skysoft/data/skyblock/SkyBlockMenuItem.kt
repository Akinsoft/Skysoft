package com.skysoft.data.skyblock

import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import net.minecraft.world.item.ItemStack

object SkyBlockMenuItem {
    const val SKYBLOCK_MENU_ID = "SKYBLOCK_MENU"
    const val SKYBLOCK_MENU_SLOT = 8

    fun ItemStack.isSkyBlockMenu(): Boolean = skyBlockId() == SKYBLOCK_MENU_ID
}
