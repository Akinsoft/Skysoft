package com.skysoft.integration

import com.skysoft.features.inventory.AnimatedDyeArmorCache
import com.skysoft.features.inventory.ContainerSearchHighlighter
import com.skysoft.features.inventory.ExperimentationTableHelper
import com.skysoft.features.inventory.InventoryEquipment
import com.skysoft.features.inventory.RarityHighlightRenderer
import com.skysoft.features.inventory.SmoothSwapping
import com.skysoft.features.misc.PlayerHeadSkinFix
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

object ContainerItemRenderHooks {
    @JvmStatic
    fun shouldRenderSlotItem(
        boundary: String,
        screen: AbstractContainerScreen<*>,
        slot: Slot?,
    ): Boolean = SkysoftErrorBoundary.value(boundary, true) {
        InventoryEquipment.isEquipmentSlot(slot) || !SmoothSwapping.shouldSuppressSlot(screen, slot)
    }

    @JvmStatic
    fun containerRenderStack(screen: AbstractContainerScreen<*>, slot: Slot?, stack: ItemStack): ItemStack? {
        val rememberedStack = SkysoftErrorBoundary.value<ItemStack?>(
            "Experimentation Table remembered item",
            stack,
        ) {
            ExperimentationTableHelper.displayStack(screen, slot, stack)
        } ?: return null
        return SkysoftErrorBoundary.value<ItemStack?>("Player Head Skin inventory item", rememberedStack) {
            PlayerHeadSkinFix.inventoryStack(slot, rememberedStack)
        }
    }

    @JvmStatic
    fun renderItemWithRarity(
        boundary: String,
        screen: AbstractContainerScreen<*>,
        context: GuiGraphicsExtractor,
        slot: Slot?,
        stack: ItemStack,
        render: Runnable,
    ) {
        SkysoftErrorBoundary.run("Container Search background") {
            if (slot != null) ContainerSearchHighlighter.renderBackground(context, slot)
        }
        val rarityStack = SkysoftErrorBoundary.value("Animated dye armor rarity", stack) {
            AnimatedDyeArmorCache.displayStack(screen, slot, stack)
        }
        SkysoftErrorBoundary.aroundUnit(boundary, render::run) { renderItem ->
            RarityHighlightRenderer.renderContainerItem(rarityStack, renderItem)
        }
    }
}
