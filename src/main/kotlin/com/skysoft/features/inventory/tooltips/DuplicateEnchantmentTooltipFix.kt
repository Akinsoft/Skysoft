package com.skysoft.features.inventory.tooltips

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.ItemTooltipEvents
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
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
                val line = enchantmentLine(enchantment.key, enchantment.intValue)
                if (line !in lore) continue
                val first = (1 until tooltip.size).firstOrNull { tooltip[it].string == line } ?: continue
                if ((first + 1 until tooltip.size).any { tooltip[it].string == line }) tooltip.removeAt(first)
            }
        }
    }

    private fun enchantmentLine(enchantment: Holder<Enchantment>, level: Int): String {
        val name = enchantment.value().description().string
        if (level == 1 && enchantment.value().maxLevel == 1) return name
        return "$name ${Component.translatable("enchantment.level.$level").string}"
    }
}
