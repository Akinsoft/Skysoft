package com.skysoft.features.inventory

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.ItemTooltipEvents
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.item.enchantment.Enchantment

object DuplicateEnchantmentTooltipFix {
    fun register() {
        ItemTooltipEvents.register(
            "Duplicate enchantment tooltip",
            isActive = { HypixelLocationState.inSkyBlock },
        ) tooltip@{ stack, _, _, tooltip ->
            if (!stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT)
                    .shows(DataComponents.ENCHANTMENTS)
            ) return@tooltip
            val enchantments = stack.get(DataComponents.ENCHANTMENTS)?.takeUnless { it.isEmpty } ?: return@tooltip
            val lore = stack.get(DataComponents.LORE)
                ?.lines()
                ?.mapNotNullTo(hashSetOf()) { it.string.takeIf(String::isNotBlank) }
                ?: return@tooltip
            for (enchantment in enchantments.entrySet()) {
                val line = Enchantment.getFullname(enchantment.key, enchantment.intValue).string
                if (line !in lore) continue
                val first = (1 until tooltip.size).firstOrNull { tooltip[it].string == line } ?: continue
                if ((first + 1 until tooltip.size).any { tooltip[it].string == line }) tooltip.removeAt(first)
            }
        }
    }
}
