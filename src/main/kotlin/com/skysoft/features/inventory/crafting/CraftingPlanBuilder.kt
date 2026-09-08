package com.skysoft.features.inventory.crafting

import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.RecipeIngredient
import com.skysoft.data.skyblock.RecipeIngredientKeyContext
import com.skysoft.data.skyblock.RecipeIngredientKind
import com.skysoft.data.skyblock.SkyBlockCurrencyStacks
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockRecipe
import com.skysoft.data.skyblock.SkyBlockRecipeType
import com.skysoft.data.skyblock.itemListKey
import com.skysoft.data.skyblock.price.BazaarProductAvailability
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.data.skyblock.recipeIngredientStack
import net.minecraft.world.item.ItemStack

internal fun buildCraftingPlan(
    targets: Map<String, Long>,
    available: MutableMap<String, Long>,
): List<CraftingHelperLine> = buildList {
    val builder = CraftingPlanBuilder(this, available)
    targets.forEach { (itemId, amount) ->
        builder.append(
            ingredient = RecipeIngredient(itemId, amount),
            required = amount,
            depth = 0,
            ancestorHasMore = emptyList(),
            isLast = true,
            ancestors = emptySet(),
            supercraft = null,
        )
    }
}

private class CraftingPlanBuilder(
    private val lines: MutableList<CraftingHelperLine>,
    private val available: MutableMap<String, Long>,
) {
    fun append(
        ingredient: RecipeIngredient,
        required: Long,
        depth: Int,
        ancestorHasMore: List<Boolean>,
        isLast: Boolean,
        ancestors: Set<String>,
        supercraft: CraftingHelperSupercraft?,
    ) {
        if (required <= 0L) return
        val key = ingredient.itemListKey(RecipeIngredientKeyContext.VIEWER)
        val marker = key?.serialized() ?: "${ingredient.kind}:${ingredient.id}"
        val recipe = key?.takeUnless { marker in ancestors }?.let(::supportedRecipe)
        val owned = ingredient.takeIf { it.kind == RecipeIngredientKind.ITEM }?.let {
            available.getOrDefault(ingredient.id, 0L)
        }
        val used = owned?.coerceAtMost(required) ?: 0L
        if (owned != null) available[ingredient.id] = owned - used
        val presentation = ingredientPresentation(ingredient, key, required)
        lines += CraftingHelperLine(
            itemId = ingredient.id,
            key = key,
            required = required,
            owned = owned,
            prefix = treePrefix(depth, ancestorHasMore, isLast),
            formattedName = presentation.formattedName,
            plainName = presentation.plainName,
            stack = presentation.stack,
            acquisition = acquisitionFor(key, recipe),
            missing = required - used,
            supercraft = supercraft.takeIf { used == required },
            isTarget = depth == 0,
        )
        val missing = required - used
        val outputCount = recipe?.result?.count ?: return
        if (missing <= 0L || outputCount <= 0L) return
        val crafts = (missing - 1L) / outputCount + 1L
        val ingredients = combinedIngredients(recipe).map { child ->
            child.copy(count = Math.multiplyExact(child.count, crafts))
        }
        val nextAncestors = ancestors + marker
        val nextAncestorHasMore = if (depth == 0) emptyList() else ancestorHasMore + !isLast
        val childSupercraft = CraftingHelperSupercraft(recipe.result.id, presentation.plainName, crafts)
            .takeIf { recipe.type == SkyBlockRecipeType.CRAFTING && hasAllIngredients(ingredients) }
        ingredients.forEachIndexed { index, child ->
            append(
                ingredient = child,
                required = child.count,
                depth = depth + 1,
                ancestorHasMore = nextAncestorHasMore,
                isLast = index == ingredients.lastIndex,
                ancestors = nextAncestors,
                supercraft = childSupercraft,
            )
        }
    }

    private fun hasAllIngredients(ingredients: List<RecipeIngredient>): Boolean {
        val remaining = available.toMutableMap()
        return ingredients.all { ingredient ->
            if (ingredient.kind != RecipeIngredientKind.ITEM) return false
            val amount = remaining.getOrDefault(ingredient.id, 0L)
            remaining[ingredient.id] = amount - ingredient.count
            amount >= ingredient.count
        }
    }
}

internal fun combinedIngredients(recipe: SkyBlockRecipe): List<RecipeIngredient> {
    val ingredients = buildList {
        addAll(recipe.ingredients)
        if (recipe is SkyBlockRecipe.Process && recipe.coins > 0L) {
            add(RecipeIngredient("COIN", recipe.coins, RecipeIngredientKind.CURRENCY, "Coins"))
        }
    }
    val combined = linkedMapOf<RecipeIngredient, Long>()
    ingredients.filter { it.count > 0L }.forEach { ingredient ->
        val identity = ingredient.copy(count = 0L)
        combined[identity] = Math.addExact(combined.getOrDefault(identity, 0L), ingredient.count)
    }
    return combined.map { (ingredient, count) -> ingredient.copy(count = count) }
}

internal fun supportedRecipe(key: ItemListEntryKey): SkyBlockRecipe? =
    SkyBlockDataRepository.recipesFor(key).firstOrNull { recipe ->
        recipe.type == SkyBlockRecipeType.CRAFTING || recipe.type == SkyBlockRecipeType.FORGE
    }

private fun ingredientPresentation(
    ingredient: RecipeIngredient,
    key: ItemListEntryKey?,
    required: Long,
): CraftingIngredientPresentation {
    val entry = key?.let(SkyBlockDataRepository::entry)
    val plainName = entry?.displayName ?: ingredient.displayName ?: ingredient.id.replace('_', ' ')
    val formattedName = entry?.formattedDisplayName ?: when (ingredient.kind) {
        RecipeIngredientKind.CURRENCY -> "§6$plainName"
        else -> "§f$plainName"
    }
    val stack = when (ingredient.kind) {
        RecipeIngredientKind.CURRENCY -> SkyBlockCurrencyStacks.supportedStack(ingredient.id, required)
        else -> recipeIngredientStack(ingredient) ?: key?.let(SkyBlockDataRepository::displayStack)
    }
    return CraftingIngredientPresentation(formattedName, plainName, stack)
}

private fun acquisitionFor(key: ItemListEntryKey?, recipe: SkyBlockRecipe?): CraftingAcquisition? {
    if (recipe?.type == SkyBlockRecipeType.FORGE) return CraftingAcquisition.FORGE
    if (key?.kind != ItemListEntryKind.SKYBLOCK) return null
    return when {
        SkyBlockPriceData.bazaarAvailability(key.id) == BazaarProductAvailability.AVAILABLE ->
            CraftingAcquisition.BAZAAR
        SkyBlockPriceData.lowestBinAvailability(key.id) == BazaarProductAvailability.AVAILABLE ->
            CraftingAcquisition.AUCTION_HOUSE
        else -> null
    }
}

private fun treePrefix(depth: Int, ancestorHasMore: List<Boolean>, isLast: Boolean): String = buildString {
    ancestorHasMore.forEach { hasMore -> append(if (hasMore) "│ " else "  ") }
    if (depth > 0) append(if (isLast) "└ " else "├ ")
}

internal data class CraftingHelperLine(
    val itemId: String,
    val key: ItemListEntryKey?,
    val required: Long,
    val owned: Long?,
    val prefix: String,
    val formattedName: String,
    val plainName: String,
    val stack: ItemStack?,
    val acquisition: CraftingAcquisition?,
    val missing: Long,
    val supercraft: CraftingHelperSupercraft?,
    val isTarget: Boolean,
)

internal data class CraftingHelperSupercraft(
    val itemId: String,
    val itemName: String,
    val crafts: Long,
)

internal enum class CraftingAcquisition(val displayName: String) {
    BAZAAR("Bazaar"),
    AUCTION_HOUSE("Auction House"),
    FORGE("Forge"),
}

private data class CraftingIngredientPresentation(
    val formattedName: String,
    val plainName: String,
    val stack: ItemStack?,
)
