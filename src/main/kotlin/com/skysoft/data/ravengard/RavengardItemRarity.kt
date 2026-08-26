package com.skysoft.data.ravengard

import java.util.Optional
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

object RavengardItemRarity {
    fun color(stack: ItemStack): Int? {
        if (stack.isEmpty) return null
        return ravengardRarityColor(
            stack.get(DataComponents.TOOLTIP_STYLE),
            stack.get(DataComponents.ITEM_MODEL),
            stack.hoverName,
        )
    }
}

internal fun ravengardRarityColor(
    tooltipStyle: Identifier?,
    itemModel: Identifier?,
    name: Component,
): Int? {
    if (tooltipStyle?.namespace != RAVENGARD_NAMESPACE) return null
    if (tooltipStyle.path !in RARITY_STYLES) return null
    if (itemModel?.namespace != RAVENGARD_NAMESPACE) return null
    return name.visit({ style: Style, text: String ->
        Optional.ofNullable(style.color?.value?.takeIf { text.isNotBlank() })
    }, Style.EMPTY).orElse(null)
}

private val RARITY_STYLES = setOf("common", "uncommon", "rare")
internal const val RAVENGARD_NAMESPACE = "hypixel_ravengard"
