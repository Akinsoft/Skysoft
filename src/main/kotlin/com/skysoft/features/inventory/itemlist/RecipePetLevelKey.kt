package com.skysoft.features.inventory.itemlist

import com.skysoft.data.skyblock.RecipeIngredientKind
import com.skysoft.data.skyblock.SkyBlockRecipe
import com.skysoft.data.skyblock.SkyBlockRecipeType

internal class RecipePetLevelKey(
    val recipe: SkyBlockRecipe,
    val petName: String,
) {
    override fun equals(other: Any?): Boolean =
        other is RecipePetLevelKey && recipe === other.recipe && petName == other.petName

    override fun hashCode(): Int = 31 * System.identityHashCode(recipe) + petName.hashCode()
}

internal fun recipePetLevelKey(recipe: SkyBlockRecipe, ingredientId: String): RecipePetLevelKey =
    RecipePetLevelKey(recipe, ingredientId.substringBefore(';'))

internal fun displayedProcessCoins(recipe: SkyBlockRecipe.Process, petLevels: Map<RecipePetLevelKey, Int>): Long {
    if (recipe.type != SkyBlockRecipeType.KAT) return recipe.coins
    val pet = recipe.ingredients.firstOrNull { it.kind == RecipeIngredientKind.PET } ?: return recipe.coins
    return katUpgradeCoins(recipe.coins, petLevels[recipePetLevelKey(recipe, pet.id)] ?: 1)
}

private fun katUpgradeCoins(baseCoins: Long, petLevel: Int): Long {
    if (baseCoins <= 0) return 0
    val discountedLevels = petLevel.coerceIn(1, KAT_DISCOUNT_LEVEL_CAP)
    return baseCoins * (KAT_DISCOUNT_PERMILLE - discountedLevels * KAT_DISCOUNT_PER_LEVEL_PERMILLE) /
        KAT_DISCOUNT_PERMILLE
}

private const val KAT_DISCOUNT_LEVEL_CAP = 100
private const val KAT_DISCOUNT_PER_LEVEL_PERMILLE = 3L
private const val KAT_DISCOUNT_PERMILLE = 1_000L
