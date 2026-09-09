package com.skysoft.features.pets

import com.skysoft.utils.ItemTooltipEvents
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component

internal object PetExpShareTooltip {
    fun register() {
        ItemTooltipEvents.register("Active Pet Tooltip rendering", isActive = { true }) tooltip@{ stack, _, _, tooltip ->
            val screen = MinecraftClient.screen() as? AbstractContainerScreen<*> ?: return@tooltip
            if (!PetStorageService.isExpSharingInventory(screen.title.cleanSkyBlockText())) return@tooltip
            val slot = screen.menu.slots.firstOrNull { it.item === stack }
                ?: screen.menu.slots.firstOrNull {
                    it.item == stack && PetStorageService.isExpShareSlotDisabled(it.containerSlot)
                }
                ?: return@tooltip
            if (!PetStorageService.isExpShareSlotDisabled(slot.containerSlot)) return@tooltip

            tooltip.add(Component.literal(""))
            tooltip.add(Component.literal("This Exp Share slot is disabled.").withStyle(ChatFormatting.RED))
            tooltip.add(
                Component.literal("Diana's ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Sharing is Caring").withStyle(ChatFormatting.LIGHT_PURPLE))
                    .append(Component.literal(" perk is not active.").withStyle(ChatFormatting.GRAY)),
            )
        }
    }
}
