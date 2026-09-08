package com.skysoft.features.inventory.crafting

import com.skysoft.data.skyblock.RecipeIngredientKind
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemChangeBatch
import com.skysoft.data.skyblock.SkyBlockItemNames
import com.skysoft.data.skyblock.SkyBlockRecipeType
import com.skysoft.data.skyblock.SkyBlockSupercraft
import kotlin.math.abs

internal object CraftingHelperOptimisticInventory {
    private val changes = mutableMapOf<String, OptimisticChange>()

    val hasChanges: Boolean
        get() = changes.isNotEmpty()

    fun record(supercraft: SkyBlockSupercraft) {
        val itemId = SkyBlockItemNames.itemId(supercraft.displayName) ?: return
        val recipe = SkyBlockDataRepository.recipesFor(SkyBlockDataRepository.itemKey(itemId))
            .firstOrNull { it.type == SkyBlockRecipeType.CRAFTING }
            ?: return
        val outputCount = recipe.result.count
        if (outputCount <= 0L) return
        val outputAmount = supercraft.amount.toLong()
        val crafts = (outputAmount - 1L) / outputCount + 1L
        add(recipe.result.id, outputAmount)
        combinedIngredients(recipe)
            .filter { ingredient -> ingredient.kind == RecipeIngredientKind.ITEM && ingredient.alternatives.isEmpty() }
            .forEach { ingredient -> add(ingredient.id, -Math.multiplyExact(ingredient.count, crafts)) }
    }

    fun reconcile(batch: SkyBlockItemChangeBatch) {
        discardExpired()
        batch.changes.forEach { (itemId, change) ->
            val pending = changes[itemId] ?: return@forEach
            val observed = change.toLong()
            if (pending.sign != observed.compareTo(0L)) return@forEach
            if (abs(observed) >= abs(pending.amount)) {
                changes.remove(itemId)
            } else {
                changes[itemId] = pending.copy(amount = pending.amount - observed)
            }
        }
    }

    fun applyTo(available: MutableMap<String, Long>) {
        discardExpired()
        changes.forEach { (itemId, change) ->
            val amount = Math.addExact(available.getOrDefault(itemId, 0L), change.amount).coerceAtLeast(0L)
            if (amount == 0L) available.remove(itemId) else available[itemId] = amount
        }
    }

    fun clear() = changes.clear()

    private fun add(itemId: String, amount: Long) {
        if (amount == 0L) return
        discardExpired()
        val updated = Math.addExact(changes[itemId]?.amount ?: 0L, amount)
        if (updated == 0L) {
            changes.remove(itemId)
        } else {
            changes[itemId] = OptimisticChange(
                amount = updated,
                expiresAtMillis = System.currentTimeMillis() + OPTIMISTIC_CHANGE_MILLIS,
            )
        }
    }

    private fun discardExpired() {
        val now = System.currentTimeMillis()
        changes.entries.removeIf { (_, change) -> change.expiresAtMillis <= now }
    }

    private data class OptimisticChange(
        val amount: Long,
        val expiresAtMillis: Long,
    ) {
        val sign: Int
            get() = amount.compareTo(0L)
    }
}

private const val OPTIMISTIC_CHANGE_MILLIS = 10_000L
