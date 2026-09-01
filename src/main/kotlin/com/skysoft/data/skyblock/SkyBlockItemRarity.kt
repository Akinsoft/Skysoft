package com.skysoft.data.skyblock

import com.skysoft.utils.ColorUtilities.RGB_MASK
import java.util.Optional
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

object SkyBlockItemRarity {
    fun fromInternalName(internalName: String?): SkyBlockRarity? {
        val cleanInternalName = internalName?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val rarityName = SkyBlockDataRepository.info(SkyBlockDataRepository.itemKey(cleanInternalName))?.rarity
            ?: return null
        return SkyBlockRarity.getByName(rarityName.replace(' ', '_'))
    }

    fun from(stack: ItemStack): SkyBlockRarity? {
        if (stack.isEmpty) return null
        return rarityFromTooltipStyle(stack.get(DataComponents.TOOLTIP_STYLE))
            ?: rarityFromLore(stack.get(DataComponents.LORE)?.lines().orEmpty())
    }
}

internal fun rarityFromTooltipStyle(style: Identifier?): SkyBlockRarity? {
    if (style?.namespace != SKYBLOCK_TOOLTIP_NAMESPACE) return null
    return SkyBlockRarity.getByName(style.path)
}

internal fun rarityFromLore(lines: List<Component>): SkyBlockRarity? =
    itemClassificationFromLore(lines)?.rarity

internal fun itemCategoryFromLore(lines: List<Component>): String? =
    itemClassificationFromLore(lines)?.category

private fun itemClassificationFromLore(lines: List<Component>): ItemLoreClassification? =
    lines.asReversed().firstNotNullOfOrNull { line ->
        line.visit({ style: Style, text: String ->
            val classification = RARITY_NAME_PATTERN.find(text)?.let { match ->
                match.value
                    .replace(' ', '_')
                    .let(SkyBlockRarity::getByName)
                    ?.takeIf { rarity ->
                        style.isBold && style.color?.value == (rarity.color.rgb and RGB_MASK)
                    }
                    ?.let { rarity ->
                        ItemLoreClassification(
                            rarity,
                            text.substring(match.range.last + 1).trim().takeIf(String::isNotEmpty),
                        )
                    }
            }
            Optional.ofNullable(classification)
        }, Style.EMPTY).orElse(null)
    }

private data class ItemLoreClassification(
    val rarity: SkyBlockRarity,
    val category: String?,
)

private val RARITY_NAME_PATTERN = Regex(
    SkyBlockRarity.entries
        .sortedByDescending { it.name.length }
        .joinToString("|", "(?<![A-Z])(?:", ")(?![A-Z])") { it.name.replace('_', ' ') },
)

private const val SKYBLOCK_TOOLTIP_NAMESPACE = "hypixel_skyblock"
