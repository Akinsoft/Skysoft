package com.skysoft.data.skyblock

import com.skysoft.utils.ColorUtilities.RGB_MASK
import java.util.Optional
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
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
        val rarity = rarityFromTooltipStyle(stack.get(DataComponents.TOOLTIP_STYLE))
            ?: rarityFromLore(stack.get(DataComponents.LORE)?.lines().orEmpty())
        return rarity.also {
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

internal fun rarityFromLore(lines: List<Component>): SkyBlockRarity? =
    lines.asReversed().firstNotNullOfOrNull { line ->
        line.visit({ style: Style, text: String ->
            val rarity = RARITY_NAME_PATTERN.find(text)
                ?.value
                ?.replace(' ', '_')
                ?.let(SkyBlockRarity::getByName)
                ?.takeIf { candidate ->
                    style.isBold && style.color?.value == (candidate.color.rgb and RGB_MASK)
                }
            Optional.ofNullable(rarity)
        }, Style.EMPTY).orElse(null)
    }

private val RARITY_NAME_PATTERN = Regex(
    SkyBlockRarity.entries
        .sortedByDescending { it.name.length }
        .joinToString("|", "(?<![A-Z])(?:", ")(?![A-Z])") { it.name.replace('_', ' ') },
)

private const val SKYBLOCK_TOOLTIP_NAMESPACE = "hypixel_skyblock"
