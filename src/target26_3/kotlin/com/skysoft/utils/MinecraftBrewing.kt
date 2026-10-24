package com.skysoft.utils

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.resources.RegistryOps
import net.minecraft.server.packs.repository.ServerPacksSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.crafting.BrewingRecipe
import net.minecraft.world.item.crafting.PotionIngredient
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity

object MinecraftBrewing {
    const val BREWING_TIME_SECONDS = BrewingStandBlockEntity.BREWING_TIME_SECONDS
    private const val BREWING_RECIPE_DIRECTORY = "recipe/brewing"

    fun mixes(level: Level): List<BrewingMix> = vanillaBrewingRecipes(level).flatMap { recipe ->
        val output = recipe.output.create()
        val reagents = recipe.reagent.stacks()
        recipe.input.stacks().flatMap { input -> reagents.map { reagent -> BrewingMix(input, reagent, output) } }
    }

    private fun vanillaBrewingRecipes(level: Level): List<BrewingRecipe> {
        val ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess())
        val pack = ServerPacksSource.createVanillaPackSource()
        return pack.fullResources().use {
            pack.asResourceManager()
                .listResources(BREWING_RECIPE_DIRECTORY) { id -> id.path.endsWith(".json") }
                .values
                .map { resource ->
                    val json = resource.openAsReader().use(JsonParser::parseReader)
                    BrewingRecipe.MAP_CODEC.codec().parse(ops, json).getOrThrow()
                }
        }
    }

    private fun PotionIngredient.stacks(): List<ItemStack> {
        val potions = potions().flatMap { it.potions() }.map { it.toList() }.orElse(null)
        return ingredient().items().toList().flatMap { item ->
            potions?.map { potion -> PotionContents.createItemStack(item.value(), potion) } ?: listOf(ItemStack(item))
        }
    }
}
