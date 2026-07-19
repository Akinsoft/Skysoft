package com.skysoft.data.skyblock

import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

object SkyBlockItemRarity {
    private val rarityByHash = mutableMapOf<Int, MutableList<CachedRarity>>()

    fun from(stack: ItemStack): SkyBlockRarity? {
        if (stack.isEmpty) return null
        val hash = ItemStack.hashItemAndComponents(stack)
        rarityByHash[hash]?.firstOrNull { ItemStack.isSameItemSameComponents(it.stack, stack) }?.let {
            return it.rarity
        }
        if (rarityByHash.size >= MAX_CACHED_ITEM_HASHES) rarityByHash.clear()
        return rarityFromTooltipStyle(stack.get(DataComponents.TOOLTIP_STYLE)).also { rarity ->
            rarityByHash.getOrPut(hash) { mutableListOf() } += CachedRarity(stack.copyWithCount(1), rarity)
        }
    }

    private data class CachedRarity(val stack: ItemStack, val rarity: SkyBlockRarity?)

    private const val MAX_CACHED_ITEM_HASHES = 512
}

internal fun rarityFromTooltipStyle(style: Identifier?): SkyBlockRarity? {
    if (style?.namespace != SKYBLOCK_TOOLTIP_NAMESPACE) return null
    return SkyBlockRarity.getByName(style.path)
}

private const val SKYBLOCK_TOOLTIP_NAMESPACE = "hypixel_skyblock"
