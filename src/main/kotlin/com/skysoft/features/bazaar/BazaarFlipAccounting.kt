package com.skysoft.features.bazaar

import com.skysoft.data.skyblock.BazaarOrderType
import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageView

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

internal fun trackedInvestedValue(data: ProfileStorageView.BazaarTrackerData): Double =
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
) {
    val batchId = order.flipBatchId ?: return
    if (amount <= 0L || unitCost <= 0.0) return
    data.itemLots += ProfileStorage.BazaarItemLotData(
        itemName = order.itemName,
        productId = order.productId,
        amount = amount,
        unitCost = unitCost,
        flipBatchId = batchId,
    )
}

internal fun ProfileStorage.BazaarTrackerData.prepareCraftedCostBasis(
    productId: String?,
    itemName: String,
    neededAmount: Long,
) {
    if (productId == null) return
    val shortfall = neededAmount - trackedLotAmount(this, productId, itemName)
    if (shortfall <= 0L) return
    val recipes = BazaarCraftingRecipes.recipesFor(productId) ?: return
    val candidates = recipes
        .distinctBy { it.outputCount to it.ingredients }
        .flatMap { recipe -> craftPlans(this, recipe, shortfall) }
    applyCraftPlan(this, productId, itemName, candidates.singleOrNull() ?: return)
}

internal fun consumeTrackedLotsForSale(
    data: ProfileStorage.BazaarTrackerData,
    productId: String?,
    itemName: String,
    soldAmount: Long,
    revenue: Double,
): Double {
    val consumed = consumeLots(data, soldAmount) { lot -> lot.flipBatchId != null && lotMatches(lot, productId, itemName) }
    val knownRevenue = revenue * (consumed.amount.toDouble() / soldAmount.coerceAtLeast(1L))
    return knownRevenue - consumed.cost
}

private data class CraftPlan(
    val outputAmount: Long,
    val ingredientAmounts: Map<String, Long>,
    val batchId: Long,
)

private data class ConsumedLots(val amount: Long, val cost: Double)

private fun consumeLots(
    data: ProfileStorage.BazaarTrackerData,
    amount: Long,
    matches: (ProfileStorage.BazaarItemLotData) -> Boolean,
): ConsumedLots {
    var remaining = amount
    var cost = 0.0
    val iterator = data.itemLots.iterator()
    while (iterator.hasNext() && remaining > 0L) {
        val lot = iterator.next()
        if (!matches(lot)) continue
        val used = minOf(remaining, lot.amount)
        cost += used * lot.unitCost
        lot.amount -= used
        remaining -= used
        if (lot.amount <= 0L) iterator.remove()
    }
    return ConsumedLots(amount - remaining, cost)
}

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
    val totalCost = plan.ingredientAmounts.entries.sumOf { (ingredientId, requiredAmount) ->
        val consumed = consumeLots(data, requiredAmount) { lot ->
            lot.flipBatchId == plan.batchId && productMatches(lot.productId, ingredientId)
        }
        check(consumed.amount == requiredAmount) { "Craft plan changed before it was applied" }
        consumed.cost
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

data class BazaarInvestmentPosition(
    val amount: Long,
    val investedCoins: Double,
    val averageCost: Double,
)

internal fun bazaarInvestmentPosition(
    lots: List<ProfileStorageView.BazaarItemLotData>,
    productId: String,
): BazaarInvestmentPosition? {
    val matching = lots.asSequence()
        .filter { it.flipBatchId != null && it.productId.equals(productId, ignoreCase = true) }
        .toList()
    val amount = matching.sumOf { it.amount }
    if (amount <= 0L) return null
    val investedCoins = matching.sumOf { it.amount * it.unitCost }
    return BazaarInvestmentPosition(
        amount = amount,
        investedCoins = investedCoins,
        averageCost = investedCoins / amount,
    )
}
