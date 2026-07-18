package com.skysoft.features.inventory

import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.RecipeIngredient
import com.skysoft.data.skyblock.RecipeIngredientKind
import com.skysoft.data.skyblock.SkyBlockRecipe
import com.skysoft.data.skyblock.SkyBlockRecipeType
import com.skysoft.data.skyblock.expandedOptions
import java.util.ArrayDeque
import java.util.concurrent.CancellationException

internal interface RawCraftPriceSource {
    val recipeVersion: Long
    val marketVersion: Long
    val recipeKeys: Set<ItemListEntryKey>

    fun recipesFor(key: ItemListEntryKey): List<SkyBlockRecipe>
    fun bazaarInstantBuy(itemId: String): Double?
    fun lowestBin(itemId: String): Double?
}

internal data class RawCraftCostResolution(
    val costs: Map<String, Double>,
    val stats: RawCraftCostResolutionStats,
)

internal data class RawCraftCostResolutionStats(
    val requestedItems: Int,
    val visitedItems: Int,
    val candidateRecipes: Int,
    val excludedRecipes: Int,
    val recipeEvaluations: Int,
    val unavailableIngredients: Int,
    val invalidRecipes: Int,
    val passes: Int,
)

internal class RawCraftCostResolver(
    private val source: RawCraftPriceSource,
) {
    private var recipeVersion = Long.MIN_VALUE
    private var marketVersion = Long.MIN_VALUE
    private val cachedCosts = mutableMapOf<String, Double?>()
    private val cachedRecipes = mutableMapOf<ItemListEntryKey, List<SkyBlockRecipe>>()

    fun cost(itemId: String): Double? {
        invalidateIfNeeded()
        if (cachedCosts.containsKey(itemId)) return cachedCosts[itemId]
        val key = ItemListEntryKey(ItemListEntryKind.SKYBLOCK, itemId)
        resolve(setOf(key)) { false }
        return cachedCosts[itemId]
    }

    fun resolveAll(isCancelled: () -> Boolean = { false }): RawCraftCostResolution {
        invalidateIfNeeded()
        val roots = source.recipeKeys.filterTo(linkedSetOf()) { it.kind == ItemListEntryKind.SKYBLOCK }
        return resolve(roots, isCancelled)
    }

    private fun invalidateIfNeeded() {
        val currentRecipeVersion = source.recipeVersion
        val currentMarketVersion = source.marketVersion
        if (recipeVersion == currentRecipeVersion && marketVersion == currentMarketVersion) return
        recipeVersion = currentRecipeVersion
        marketVersion = currentMarketVersion
        cachedCosts.clear()
        cachedRecipes.clear()
    }

    private fun resolve(
        roots: Set<ItemListEntryKey>,
        isCancelled: () -> Boolean,
    ): RawCraftCostResolution {
        val stats = MutableRawCraftCostResolutionStats(roots.size)
        val recipesByResult = collectReachableRecipes(roots, stats, isCancelled)
        val keys = recipesByResult.keys
        val directMarketCosts = keys.mapNotNull { key ->
            directMarketCost(key)?.let { key to it }
        }.toMap()
        var acquisitionCosts = directMarketCosts
        var productionCosts = emptyMap<ItemListEntryKey, Double>()
        var shopCosts = emptyMap<ItemListEntryKey, Double>()
        var remainingPasses = MAX_RECIPE_DEPTH + 1
        while (remainingPasses-- > 0) {
            checkCancellation(isCancelled)
            val nextProductionCosts = productionCosts.toMutableMap()
            val nextShopCosts = shopCosts.toMutableMap()
            recipesByResult.forEach { (key, recipes) ->
                checkCancellation(isCancelled)
                cheapestRecipeCost(recipes, PRODUCTION_RECIPE_TYPES, acquisitionCosts, stats)?.let { cost ->
                    nextProductionCosts.putCheaper(key, cost)
                }
                cheapestRecipeCost(recipes, SHOP_RECIPE_TYPES, acquisitionCosts, stats)?.let { cost ->
                    nextShopCosts.putCheaper(key, cost)
                }
            }
            val nextAcquisitionCosts = keys.mapNotNullTo(mutableListOf()) { key ->
                listOfNotNull(
                    directMarketCosts[key],
                    nextProductionCosts[key],
                    nextShopCosts[key],
                ).minOrNull()?.let { key to it }
            }.toMap()
            stats.passes++
            val isStable = nextProductionCosts == productionCosts &&
                nextShopCosts == shopCosts &&
                nextAcquisitionCosts == acquisitionCosts
            productionCosts = nextProductionCosts
            shopCosts = nextShopCosts
            acquisitionCosts = nextAcquisitionCosts
            if (isStable) break
        }
        recipesByResult.keys.asSequence()
            .filter { it.kind == ItemListEntryKind.SKYBLOCK }
            .forEach { key -> cachedCosts[key.id] = productionCosts[key] }
        val costs = roots.mapNotNull { key ->
            productionCosts[key]?.positivePrice()?.let { key.id to it }
        }.toMap()
        return RawCraftCostResolution(costs, stats.snapshot(recipesByResult))
    }

    private fun collectReachableRecipes(
        roots: Set<ItemListEntryKey>,
        stats: MutableRawCraftCostResolutionStats,
        isCancelled: () -> Boolean,
    ): Map<ItemListEntryKey, List<SkyBlockRecipe>> {
        val pending = ArrayDeque(roots)
        val visited = linkedSetOf<ItemListEntryKey>()
        val recipesByResult = linkedMapOf<ItemListEntryKey, List<SkyBlockRecipe>>()
        while (pending.isNotEmpty()) {
            checkCancellation(isCancelled)
            val key = pending.removeFirst()
            if (!visited.add(key)) continue
            val allRecipes = cachedRecipes.getOrPut(key) { source.recipesFor(key) }
            val recipes = allRecipes.filter { it.type in ALL_ACQUISITION_RECIPE_TYPES }
            stats.excludedRecipes += allRecipes.size - recipes.size
            recipesByResult[key] = recipes
            recipes.asSequence()
                .flatMap { it.ingredients.asSequence() }
                .flatMap { it.expandedOptions() }
                .mapNotNull { it.itemKey() }
                .filterNot(visited::contains)
                .forEach(pending::addLast)
        }
        stats.visitedItems = visited.size
        return recipesByResult
    }

    private fun cheapestRecipeCost(
        recipes: List<SkyBlockRecipe>,
        allowedTypes: Set<SkyBlockRecipeType>,
        acquisitionCosts: Map<ItemListEntryKey, Double>,
        stats: MutableRawCraftCostResolutionStats,
    ): Double? = recipes.asSequence()
        .filter { it.type in allowedTypes }
        .mapNotNull { recipe -> recipeCost(recipe, acquisitionCosts, stats) }
        .minOrNull()

    private fun recipeCost(
        recipe: SkyBlockRecipe,
        acquisitionCosts: Map<ItemListEntryKey, Double>,
        stats: MutableRawCraftCostResolutionStats,
    ): Double? {
        stats.recipeEvaluations++
        val outputCount = recipe.result.count
        if (outputCount <= 0L) {
            stats.invalidRecipes++
            return null
        }
        var total = (recipe as? SkyBlockRecipe.Process)?.coins?.toDouble() ?: 0.0
        if (!total.isFinite() || total < 0.0) {
            stats.invalidRecipes++
            return null
        }
        recipe.ingredients.forEach { ingredient ->
            val ingredientCost = ingredientCost(ingredient, acquisitionCosts)
            if (ingredientCost == null) {
                stats.unavailableIngredients++
                return null
            }
            total += ingredientCost
            if (!total.isFinite()) {
                stats.invalidRecipes++
                return null
            }
        }
        return (total / outputCount).positivePrice().also {
            if (it == null) stats.invalidRecipes++
        }
    }

    private fun ingredientCost(
        ingredient: RecipeIngredient,
        acquisitionCosts: Map<ItemListEntryKey, Double>,
    ): Double? = ingredient.expandedOptions().mapNotNull { option ->
        if (option.count <= 0L) return@mapNotNull null
        val unitCost = when (option.kind) {
            RecipeIngredientKind.CURRENCY -> if (option.id == COIN_CURRENCY_ID) 1.0 else null
            else -> option.itemKey()?.let(acquisitionCosts::get)
        }
        unitCost?.let { (it * option.count).positivePrice() }
    }.minOrNull()

    private fun directMarketCost(key: ItemListEntryKey): Double? {
        if (key.kind != ItemListEntryKind.SKYBLOCK) return null
        return listOfNotNull(
            source.bazaarInstantBuy(key.id).positivePrice(),
            source.lowestBin(key.id).positivePrice(),
        ).minOrNull()
    }

    private fun RecipeIngredient.itemKey(): ItemListEntryKey? = when (kind) {
        RecipeIngredientKind.ITEM -> ItemListEntryKey(ItemListEntryKind.SKYBLOCK, id)
        RecipeIngredientKind.REGISTRY_ITEM -> ItemListEntryKey(ItemListEntryKind.REGISTRY, id)
        RecipeIngredientKind.PET,
        RecipeIngredientKind.CURRENCY,
        RecipeIngredientKind.ESSENCE,
        RecipeIngredientKind.SPECIAL,
        RecipeIngredientKind.POTION,
        -> null
    }

    private fun MutableMap<ItemListEntryKey, Double>.putCheaper(key: ItemListEntryKey, price: Double) {
        val current = this[key]
        if (current == null || price < current) this[key] = price
    }

    private fun Double?.positivePrice(): Double? = this?.takeIf { it.isFinite() && it > 0.0 }

    private fun checkCancellation(isCancelled: () -> Boolean) {
        if (isCancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
    }

    private class MutableRawCraftCostResolutionStats(
        val requestedItems: Int,
        var visitedItems: Int = 0,
        var excludedRecipes: Int = 0,
        var recipeEvaluations: Int = 0,
        var unavailableIngredients: Int = 0,
        var invalidRecipes: Int = 0,
        var passes: Int = 0,
    ) {
        fun snapshot(recipesByResult: Map<ItemListEntryKey, List<SkyBlockRecipe>>) = RawCraftCostResolutionStats(
            requestedItems = requestedItems,
            visitedItems = visitedItems,
            candidateRecipes = recipesByResult.values.sumOf(List<SkyBlockRecipe>::size),
            excludedRecipes = excludedRecipes,
            recipeEvaluations = recipeEvaluations,
            unavailableIngredients = unavailableIngredients,
            invalidRecipes = invalidRecipes,
            passes = passes,
        )
    }

    companion object {
        private const val MAX_RECIPE_DEPTH = 15
        private const val COIN_CURRENCY_ID = "COIN"
        private val PRODUCTION_RECIPE_TYPES = setOf(
            SkyBlockRecipeType.CRAFTING,
            SkyBlockRecipeType.FORGE,
            SkyBlockRecipeType.ATTRIBUTE_FUSION,
            SkyBlockRecipeType.SMELTING,
            SkyBlockRecipeType.BLASTING,
            SkyBlockRecipeType.SMOKING,
            SkyBlockRecipeType.CAMPFIRE,
            SkyBlockRecipeType.STONECUTTING,
            SkyBlockRecipeType.SMITHING,
            SkyBlockRecipeType.BREWING,
        )
        private val SHOP_RECIPE_TYPES = setOf(SkyBlockRecipeType.SHOP)
        private val ALL_ACQUISITION_RECIPE_TYPES = PRODUCTION_RECIPE_TYPES + SHOP_RECIPE_TYPES
    }
}
