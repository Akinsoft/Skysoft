package com.skysoft.features.profit

import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemChangeSource
import com.skysoft.data.skyblock.SkyBlockRecipe

private data class ProfitCraftingConversion(
    val inputId: String,
    val inputCount: Long,
    val outputCount: Long,
)

internal class ProfitCraftingReconciliation {
    private val removals = mutableMapOf<Pair<ProfitTrackerTarget, String>, PendingRemoval>()

    fun reconcile(
        target: ProfitTrackerTarget,
        source: SkyBlockItemChangeSource,
        changes: Map<String, Int>,
        allowedItems: Set<String>,
    ): Map<String, Int> {
        val now = System.currentTimeMillis()
        removals.entries.removeIf { (_, pending) -> pending.expiresAtMillis <= now }
        if (source != SkyBlockItemChangeSource.SACKS && source != SkyBlockItemChangeSource.HUNTING_BOX) {
            changes.forEach { (itemId, amount) ->
                if (amount >= 0 || itemId !in allowedItems) return@forEach
                val key = target to itemId
                removals[key] = PendingRemoval(
                    amount = removals.getOrDefault(key, PendingRemoval(0, 0)).amount - amount,
                    expiresAtMillis = now + COMPACTION_RECONCILIATION_MILLIS,
                )
            }
        }

        return buildMap {
            putAll(changes.filter { (itemId, amount) -> itemId in allowedItems && amount > 0 })
            changes.forEach { (outputId, outputAmount) ->
                if (outputAmount <= 0 || outputId !in allowedItems) return@forEach
                val conversion = craftingConversionsFor(outputId).firstOrNull { conversion ->
                    outputAmount.toLong() % conversion.outputCount == 0L &&
                        removals.containsKey(target to conversion.inputId)
                } ?: return@forEach
                val key = target to conversion.inputId
                val pending = removals.getValue(key)
                val required = conversion.inputCount * outputAmount / conversion.outputCount
                val consumed = minOf(pending.amount.toLong(), required).toInt()
                if (consumed == pending.amount) removals.remove(key) else {
                    removals[key] = pending.copy(amount = pending.amount - consumed)
                }
                put(conversion.inputId, getOrDefault(conversion.inputId, 0) - consumed)
            }
        }
    }

    fun clear(target: ProfitTrackerTarget? = null) {
        if (target == null) removals.clear() else removals.keys.removeIf { (removalTarget, _) -> removalTarget == target }
    }

    private data class PendingRemoval(val amount: Int, val expiresAtMillis: Long)
}

private fun craftingConversionsFor(outputId: String): List<ProfitCraftingConversion> =
    SkyBlockDataRepository.recipesFor(SkyBlockDataRepository.itemKey(outputId))
        .filterIsInstance<SkyBlockRecipe.Crafting>()
        .mapNotNull { recipe ->
            val inputId = recipe.singleIngredientId() ?: return@mapNotNull null
            ProfitCraftingConversion(
                inputId = inputId,
                inputCount = recipe.ingredients.sumOf { it.count },
                outputCount = recipe.result.count,
            )
        }

internal fun SkyBlockRecipe.Crafting.singleIngredientId(): String? = ingredients.map { it.id }.distinct().singleOrNull()

private const val COMPACTION_RECONCILIATION_MILLIS = 60_000L
