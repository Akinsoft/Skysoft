package com.skysoft.features.inventory.itemlist

import com.skysoft.data.skyblock.RecipeIngredient
import com.skysoft.data.skyblock.expandedOptions
import net.minecraft.world.item.ItemStack

internal fun displayedRecipeIngredient(ingredient: RecipeIngredient): RecipeIngredient {
    val options = ingredient.expandedOptions()
    val index = ((System.currentTimeMillis() / ALTERNATIVE_INGREDIENT_INTERVAL_MILLIS) % options.size).toInt()
    return options[index]
}

internal fun ItemStack.withRecipeIngredientCount(count: Long): ItemStack =
    if (count > 1) copyWithCount(Math.toIntExact(count)) else this

private const val ALTERNATIVE_INGREDIENT_INTERVAL_MILLIS = 1_000L
