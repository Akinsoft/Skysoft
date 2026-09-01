package com.skysoft.features.bazaar

import com.skysoft.data.skyblock.BazaarOrderType
import com.skysoft.data.ProfileStorage
import com.skysoft.utils.ChangeResult

internal fun activeFlipBatch(data: ProfileStorage.BazaarTrackerData): Long {
    if (data.activeFlipBatchId > 0L) return data.activeFlipBatchId
    val batchId = data.nextFlipBatchId.coerceAtLeast(1L)
    data.activeFlipBatchId = batchId
    data.nextFlipBatchId = batchId + 1L
    return batchId
}

internal fun ensureTrackedBuyOrder(
    data: ProfileStorage.BazaarTrackerData,
    order: ProfileStorage.BazaarOrderData,
    trackingEnabled: Boolean,
) {
    if (trackingEnabled && order.flipBatchId == null) order.flipBatchId = activeFlipBatch(data)
}

internal fun trackedInvestedValue(data: ProfileStorage.BazaarTrackerData): Double =
    data.activeOrders
        .asSequence()
        .filter { it.type == BazaarOrderType.BUY && it.flipBatchId != null }
        .sumOf { it.activeValue() } +
        data.itemLots.asSequence().filter { it.flipBatchId != null }.sumOf { it.amount * it.unitCost }

internal fun addTrackedBuyLot(
    data: ProfileStorage.BazaarTrackerData,
    order: ProfileStorage.BazaarOrderData,
    amount: Long,
    unitCost: Double,
): ChangeResult {
    val batchId = order.flipBatchId ?: return ChangeResult.UNCHANGED
    if (amount <= 0L || unitCost <= 0.0) return ChangeResult.UNCHANGED
    data.itemLots += ProfileStorage.BazaarItemLotData(
        itemName = order.itemName,
        productId = order.productId,
        amount = amount,
        unitCost = unitCost,
        flipBatchId = batchId,
    )
    return ChangeResult.CHANGED
}

internal fun prepareCraftedCostBasis(productId: String?, itemName: String, neededAmount: Long): CraftPreparationResult {
    if (productId == null || neededAmount <= trackedLotAmount(storage, productId, itemName)) {
        return CraftPreparationResult.NOT_NEEDED
    }
    val recipes = BazaarCraftingRecipes.recipesFor(productId)
        ?: return CraftPreparationResult.RECIPE_DATA_UNAVAILABLE
    return prepareCraftedCostBasis(storage, productId, itemName, neededAmount, recipes)
}

internal fun prepareCraftedCostBasis(
    data: ProfileStorage.BazaarTrackerData,
    productId: String,
    itemName: String,
    neededAmount: Long,
    recipes: List<BazaarCraftingRecipe>,
): CraftPreparationResult {
    val shortfall = (neededAmount - trackedLotAmount(data, productId, itemName)).coerceAtLeast(0L)
    if (shortfall == 0L) return CraftPreparationResult.NOT_NEEDED
    val candidates = recipes
        .distinctBy { it.outputCount to it.ingredients }
        .flatMap { recipe -> craftPlans(data, recipe, shortfall) }
    if (candidates.isEmpty()) return CraftPreparationResult.INSUFFICIENT_INGREDIENTS
    if (candidates.size > 1) return CraftPreparationResult.AMBIGUOUS_RECIPE
    applyCraftPlan(data, productId, itemName, candidates.single())
    return CraftPreparationResult.CONVERTED
}

internal fun consumeTrackedLotsForSale(
    data: ProfileStorage.BazaarTrackerData,
    productId: String?,
    itemName: String,
    soldAmount: Long,
    revenue: Double,
): Double {
    var remaining = soldAmount
    var knownCost = 0.0
    var knownAmount = 0L
    val iterator = data.itemLots.iterator()
    while (iterator.hasNext() && remaining > 0L) {
        val lot = iterator.next()
        if (lot.flipBatchId == null || !lotMatches(lot, productId, itemName)) continue
        val used = minOf(remaining, lot.amount)
        knownCost += used * lot.unitCost
        knownAmount += used
        lot.amount -= used
        remaining -= used
        if (lot.amount <= 0L) iterator.remove()
    }
    val knownRevenue = revenue * (knownAmount.toDouble() / soldAmount.coerceAtLeast(1L))
    return knownRevenue - knownCost
}

internal enum class CraftPreparationResult {
    NOT_NEEDED,
    CONVERTED,
    RECIPE_DATA_UNAVAILABLE,
    INSUFFICIENT_INGREDIENTS,
    AMBIGUOUS_RECIPE,
}

private data class CraftPlan(
    val outputAmount: Long,
    val ingredientAmounts: Map<String, Long>,
    val batchId: Long,
)

private fun trackedLotAmount(data: ProfileStorage.BazaarTrackerData, productId: String?, itemName: String): Long =
    data.itemLots.asSequence()
        .filter { it.flipBatchId != null && lotMatches(it, productId, itemName) }
        .sumOf { it.amount }

private fun craftPlans(
    data: ProfileStorage.BazaarTrackerData,
    recipe: BazaarCraftingRecipe,
    shortfall: Long,
): List<CraftPlan> {
    val crafts = (shortfall + recipe.outputCount - 1L) / recipe.outputCount
    val required = recipe.ingredients.mapValues { (_, amount) -> amount * crafts }
    val availableByBatch = data.itemLots
        .asSequence()
        .filter { it.flipBatchId != null && it.productId != null }
        .groupBy { it.flipBatchId!! }
        .mapValues { (_, batchLots) ->
            batchLots.groupBy { canonicalBazaarProductId(it.productId!!) }
                .mapValues { (_, lots) -> lots.sumOf { it.amount } }
        }
    return availableByBatch.mapNotNull { (batchId, available) ->
        if (required.any { (id, amount) -> available.getOrDefault(id, 0L) < amount }) return@mapNotNull null
        CraftPlan(recipe.outputCount * crafts, required, batchId)
    }
}

private fun applyCraftPlan(
    data: ProfileStorage.BazaarTrackerData,
    productId: String,
    itemName: String,
    plan: CraftPlan,
) {
    var totalCost = 0.0
    plan.ingredientAmounts.forEach { (ingredientId, requiredAmount) ->
        var remaining = requiredAmount
        val iterator = data.itemLots.iterator()
        while (iterator.hasNext() && remaining > 0L) {
            val lot = iterator.next()
            if (lot.flipBatchId != plan.batchId || !productMatches(lot.productId, ingredientId)) continue
            val used = minOf(remaining, lot.amount)
            totalCost += used * lot.unitCost
            lot.amount -= used
            remaining -= used
            if (lot.amount <= 0L) iterator.remove()
        }
        check(remaining == 0L) { "Craft plan changed before it was applied" }
    }
    data.itemLots += ProfileStorage.BazaarItemLotData(
        itemName = itemName,
        productId = productId,
        amount = plan.outputAmount,
        unitCost = totalCost / plan.outputAmount,
        flipBatchId = plan.batchId,
        source = ProfileStorage.BazaarLotSource.CRAFTED,
    )
}
