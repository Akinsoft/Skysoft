package com.skysoft.features.inventory

import de.hysky.skyblocker.skyblock.item.background.ItemBackgroundManager
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack

internal object SkyblockerItemBackgrounds {
    private val isLoaded = FabricLoader.getInstance().isModLoaded("skyblocker")

    fun draw(context: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
        if (isLoaded) LoadedSkyblockerItemBackgrounds.draw(context, stack, x, y)
    }
}

private object LoadedSkyblockerItemBackgrounds {
    fun draw(context: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
        ItemBackgroundManager.drawBackgrounds(stack, context, x, y)
    }
}
