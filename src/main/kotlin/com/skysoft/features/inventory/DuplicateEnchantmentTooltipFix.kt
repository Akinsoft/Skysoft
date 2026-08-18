package com.skysoft.features.inventory

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.SkysoftErrorBoundary
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.TooltipDisplay

object DuplicateEnchantmentTooltipFix {
    fun register() {
        ItemTooltipCallback.EVENT.register { stack, _, _, tooltip ->
            SkysoftErrorBoundary.run("Duplicate enchantment tooltip") {
                if (!HypixelLocationState.inSkyBlock) return@run
                if (!stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT)
                        .shows(DataComponents.ENCHANTMENTS)
                ) return@run
                val lore = stack.get(DataComponents.LORE)?.lines()?.mapTo(hashSetOf()) { it.string } ?: return@run
                for (line in lore) {
                    val first = (1 until tooltip.size).firstOrNull { tooltip[it].string == line } ?: continue
                    if ((first + 1 until tooltip.size).any { tooltip[it].string == line }) tooltip.removeAt(first)
                }
            }
        }
    }
}
