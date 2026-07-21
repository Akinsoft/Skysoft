package com.skysoft.features.profit

import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemChangeSource
import com.skysoft.data.skyblock.SkyBlockRecipe

internal data class ProfitCraftingConversion(
    val inputId: String,
    val inputCount: Long,
    val outputCount: Long,
)

internal class ProfitCraftingReconciliation(
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val conversionsFor: (String) -> List<ProfitCraftingConversion> = ::craftingConversionsFor,
) {
    private val removals = mutableMapOf<Pair<String, String>, PendingRemoval>()

    fun reconcile(
        preset: ProfitTrackerPreset,
        source: SkyBlockItemChangeSource,
        changes: Map<String, Int>,
        allowedItems: Set<String>,
    ): Map<String, Int> {
        val now = currentTimeMillis()
        removals.entries.removeIf { (_, pending) -> pending.expiresAtMillis <= now }
        if (source != SkyBlockItemChangeSource.SACKS && source != SkyBlockItemChangeSource.HUNTING_BOX) {
            changes.forEach { (itemId, amount) ->
                if (amount >= 0 || itemId !in allowedItems) return@forEach
                val key = preset.name to itemId
                removals[key] = PendingRemoval(
                    amount = removals.getOrDefault(key, PendingRemoval(0, 0)).amount - amount,
                    expiresAtMillis = now + COMPACTION_RECONCILIATION_MILLIS,
                )
            }
        }

        return buildMap {
            putAll(trackedItemChanges(changes, allowedItems))
            changes.forEach { (outputId, outputAmount) ->
                if (outputAmount <= 0 || outputId !in allowedItems) return@forEach
                val conversion = conversionsFor(outputId).firstOrNull { conversion ->
                    outputAmount.toLong() % conversion.outputCount == 0L &&
                        removals.containsKey(preset.name to conversion.inputId)
                } ?: return@forEach
                val key = preset.name to conversion.inputId
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

    fun clear(preset: ProfitTrackerPreset? = null) {
        if (preset == null) removals.clear() else removals.keys.removeIf { (presetName, _) -> presetName == preset.name }
    }

    private data class PendingRemoval(val amount: Int, val expiresAtMillis: Long)
}

private fun craftingConversionsFor(outputId: String): List<ProfitCraftingConversion> =
    SkyBlockDataRepository.recipesFor(SkyBlockDataRepository.itemKey(outputId))
        .filterIsInstance<SkyBlockRecipe.Crafting>()
        .mapNotNull { recipe ->
            val inputId = recipe.ingredients.map { it.id }.distinct().singleOrNull() ?: return@mapNotNull null
            ProfitCraftingConversion(
                inputId = inputId,
                inputCount = recipe.ingredients.sumOf { it.count },
                outputCount = recipe.result.count,
            )
        }

private const val COMPACTION_RECONCILIATION_MILLIS = 60_000L
