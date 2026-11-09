package com.skysoft.features.bazaar

import com.skysoft.data.skyblock.SkyBlockDataLoadState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockRecipe
import com.skysoft.data.skyblock.canonicalSkyBlockItemId

internal data class BazaarCraftingRecipe(
    val outputCount: Long,
    val ingredients: Map<String, Long>,
)

internal object BazaarCraftingRecipes {
    fun recipesFor(outputId: String): List<BazaarCraftingRecipe>? {
        val status = SkyBlockDataRepository.status.state
        if (status != SkyBlockDataLoadState.READY) return null
        return SkyBlockDataRepository.recipesFor(
            SkyBlockDataRepository.itemKey(canonicalSkyBlockItemId(outputId)),
        )
            .filterIsInstance<SkyBlockRecipe.Crafting>()
            .map { recipe ->
                BazaarCraftingRecipe(
                    outputCount = recipe.result.count,
                    ingredients = recipe.ingredients
                        .groupingBy { it.id }
                        .fold(0L) { total, ingredient -> total + ingredient.count },
                )
            }
    }
}
