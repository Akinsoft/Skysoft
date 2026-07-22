package com.skysoft.utils

import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks

object MinecraftItems {
    fun stainedGlassPanes(): List<Item> = listOf(
        Blocks.WHITE_STAINED_GLASS_PANE.asItem(),
        Blocks.ORANGE_STAINED_GLASS_PANE.asItem(),
        Blocks.MAGENTA_STAINED_GLASS_PANE.asItem(),
        Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.asItem(),
        Blocks.YELLOW_STAINED_GLASS_PANE.asItem(),
        Blocks.LIME_STAINED_GLASS_PANE.asItem(),
        Blocks.PINK_STAINED_GLASS_PANE.asItem(),
        Blocks.GRAY_STAINED_GLASS_PANE.asItem(),
        Blocks.LIGHT_GRAY_STAINED_GLASS_PANE.asItem(),
        Blocks.CYAN_STAINED_GLASS_PANE.asItem(),
        Blocks.PURPLE_STAINED_GLASS_PANE.asItem(),
        Blocks.BLUE_STAINED_GLASS_PANE.asItem(),
        Blocks.BROWN_STAINED_GLASS_PANE.asItem(),
        Blocks.GREEN_STAINED_GLASS_PANE.asItem(),
        Blocks.RED_STAINED_GLASS_PANE.asItem(),
        Blocks.BLACK_STAINED_GLASS_PANE.asItem(),
    )

    fun grayDye(): Item = Items.GRAY_DYE

    fun pinkDye(): Item = Items.PINK_DYE
}
