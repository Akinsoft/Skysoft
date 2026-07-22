package com.skysoft.utils

import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks

object MinecraftItems {
    fun stainedGlassPanes(): List<Item> = Blocks.STAINED_GLASS_PANE.asList().map { it.asItem() }

    fun grayDye(): Item = Items.DYE.gray()

    fun pinkDye(): Item = Items.DYE.pink()
}
