package com.skysoft.features.inventory.itemlist

import com.skysoft.utils.gui.Rect

internal fun itemListQuickCraftButtonBounds(result: Rect): Rect = Rect(
    result.x + result.width - QUICK_CRAFT_BUTTON_OVERLAP,
    result.y + result.height - QUICK_CRAFT_BUTTON_OVERLAP,
    QUICK_CRAFT_BUTTON_SIZE,
    QUICK_CRAFT_BUTTON_SIZE,
)

internal fun itemListQuickCraftCommand(itemId: String): String? =
    itemId.takeIf(String::isNotBlank)?.let { "viewrecipe $it" }

private const val QUICK_CRAFT_BUTTON_SIZE = 10
private const val QUICK_CRAFT_BUTTON_OVERLAP = QUICK_CRAFT_BUTTON_SIZE / 2
