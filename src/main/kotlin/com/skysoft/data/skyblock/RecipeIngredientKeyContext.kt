package com.skysoft.data.skyblock

internal enum class RecipeIngredientKeyContext {
    CATALOG_RESULT,
    CATALOG_USAGE,
    VIEWER,
    MARKET_COST,
}

internal fun RecipeIngredient.itemListKey(context: RecipeIngredientKeyContext): ItemListEntryKey? = when (kind) {
    RecipeIngredientKind.ITEM -> ItemListEntryKey(ItemListEntryKind.SKYBLOCK, id)
    RecipeIngredientKind.REGISTRY_ITEM -> ItemListEntryKey(ItemListEntryKind.REGISTRY, id)
    RecipeIngredientKind.PET -> when (context) {
        RecipeIngredientKeyContext.CATALOG_RESULT ->
            petItemKey(id) ?: ItemListEntryKey(ItemListEntryKind.SKYBLOCK, id)
        RecipeIngredientKeyContext.CATALOG_USAGE,
        RecipeIngredientKeyContext.VIEWER,
        -> petItemKey(id)
        RecipeIngredientKeyContext.MARKET_COST -> null
    }
    RecipeIngredientKind.POTION -> when (context) {
        RecipeIngredientKeyContext.CATALOG_RESULT -> ItemListEntryKey(ItemListEntryKind.SKYBLOCK, id)
        RecipeIngredientKeyContext.VIEWER -> ItemListEntryKey(ItemListEntryKind.REGISTRY, id.substringBefore('|'))
        RecipeIngredientKeyContext.CATALOG_USAGE,
        RecipeIngredientKeyContext.MARKET_COST,
        -> null
    }
    RecipeIngredientKind.CURRENCY,
    RecipeIngredientKind.ESSENCE,
    RecipeIngredientKind.SPECIAL,
    -> if (context == RecipeIngredientKeyContext.CATALOG_RESULT) {
        ItemListEntryKey(ItemListEntryKind.SKYBLOCK, id)
    } else {
        null
    }
}
