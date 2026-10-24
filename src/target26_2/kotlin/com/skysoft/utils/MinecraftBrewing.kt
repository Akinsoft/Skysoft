package com.skysoft.utils

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionBrewing
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.level.Level

object MinecraftBrewing {
    const val BREWING_TIME_SECONDS = PotionBrewing.BREWING_TIME_SECONDS
    private val potionContainers = listOf(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION)

    fun mixes(level: Level): List<BrewingMix> {
        val brewing = level.potionBrewing()
        val potionRegistry = level.registryAccess().lookupOrThrow(Registries.POTION)
        val reagents = BuiltInRegistries.ITEM.asSequence()
            .map(::ItemStack)
            .filter(brewing::isIngredient)
            .toList()
        return buildList {
            potionRegistry.listElements().forEach { potion ->
                potionContainers.forEach { container ->
                    val input = PotionContents.createItemStack(container, potion)
                    reagents.forEach { reagent ->
                        if (brewing.hasMix(input, reagent)) add(BrewingMix(input, reagent, brewing.mix(reagent, input)))
                    }
                }
            }
        }
    }
}
