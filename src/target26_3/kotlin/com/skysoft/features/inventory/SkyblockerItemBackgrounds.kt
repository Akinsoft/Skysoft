package com.skysoft.features.inventory

import com.skysoft.utils.integration.StaticIntegrationMethod
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
    private val drawBackgrounds = StaticIntegrationMethod.resolve(
        Class.forName("de.hysky.skyblocker.skyblock.item.background.ItemBackgroundManager"),
        "drawBackgrounds",
        ItemStack::class.java,
        GuiGraphicsExtractor::class.java,
        Int::class.javaPrimitiveType!!,
        Int::class.javaPrimitiveType!!,
    )

    fun draw(context: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
        StaticIntegrationMethod.invoke(drawBackgrounds, stack, context, x, y)
    }
}
