package com.skysoft.features.inventory.crafting

import com.skysoft.config.CRAFTING_HELPER_MAXIMUM_LINES
import com.skysoft.config.CRAFTING_HELPER_MAXIMUM_TARGET_AMOUNT
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.gui.OverlayControlArea

internal val craftingHelperConfig get() = SkysoftConfigGui.config().inventory.craftingHelper
internal val craftingHelperItemPanel = CraftingHelperItemPanel()
internal var craftingHelperScrollOffset = 0
internal var craftingHelperHovered = false
internal var craftingHelperHoveredControl: OverlayControlArea<CraftingHelperControl>? = null

internal fun craftingHelperLines(): List<CraftingHelperLine> {
    val storage = ProfileStorageApi.storage
    val available = buildMap {
        putAll(storage.sackContents.mapValues { (_, data) -> data.amount })
        storage.inventoryItemCounts.forEach { (itemId, amount) ->
            put(itemId, getOrDefault(itemId, 0L) + amount)
        }
    }.toMutableMap()
    CraftingHelperOptimisticInventory.applyTo(available)
    return buildCraftingPlan(craftingHelperConfig.targets, available)
}

internal fun isCraftingHelperTarget(itemId: String): Boolean =
    supportedRecipe(SkyBlockDataRepository.itemKey(itemId)) != null

internal fun addCraftingHelperTarget(itemId: String) {
    if (!isCraftingHelperTarget(itemId)) return
    modifyCraftingHelperTarget(itemId, 1L)
}

internal fun modifyCraftingHelperTarget(itemId: String, amount: Long) {
    if (amount == 0L) return
    val targets = craftingHelperConfig.targets
    val current = targets.getOrDefault(itemId, 0L)
    val updated = if (amount > 0L) {
        current + amount.coerceAtMost(CRAFTING_HELPER_MAXIMUM_TARGET_AMOUNT - current)
    } else {
        (current + amount).coerceAtLeast(0L)
    }
    if (updated == current) return
    if (updated == 0L) targets.remove(itemId) else targets[itemId] = updated
    craftingHelperScrollOffset = craftingHelperScrollOffset.coerceIn(
        0,
        craftingHelperMaximumScrollOffset(craftingHelperLines().size),
    )
    SkysoftConfigGui.config().saveNow()
}

internal fun craftingHelperMaximumScrollOffset(lineCount: Int): Int =
    (lineCount - craftingHelperConfig.settings.maximumLines.coerceIn(1, CRAFTING_HELPER_MAXIMUM_LINES))
        .coerceAtLeast(0)

internal fun clearCraftingHelperInteraction() {
    craftingHelperHovered = false
    craftingHelperHoveredControl = null
}
