package com.skysoft.integration

import com.skysoft.features.inventory.AnimatedDyeArmorCache
import com.skysoft.features.inventory.ContainerSearchHighlighter
import com.skysoft.features.inventory.ExperimentationTableHelper
import com.skysoft.features.inventory.equipment.InventoryEquipment
import com.skysoft.features.inventory.MenuKeybinds
import com.skysoft.features.inventory.RarityHighlightRenderer
import com.skysoft.features.inventory.SmoothSwapping
import com.skysoft.features.misc.PlayerHeadSkinFix
import com.skysoft.utils.SkysoftErrorBoundary
import java.util.function.BiConsumer
import java.util.function.Consumer
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

object ContainerItemRenderHooks {
    @JvmStatic
    fun renderSlotItemDecorations(
        screen: AbstractContainerScreen<*>,
        context: GuiGraphicsExtractor,
        font: Font,
        slot: Slot,
        stack: ItemStack,
        x: Int,
        y: Int,
        text: String?,
        render: BiConsumer<ItemStack, String?>,
    ) {
        if (!shouldRenderSlotItem("Smooth Swapping item decoration suppression", screen, slot)) return
        val renderStack = containerRenderStack(screen, slot, stack) ?: return
        val keybind = SkysoftErrorBoundary.value<String?>("Menu Keybind label", null) {
            MenuKeybinds.keybindLabel(screen, slot)
        }
        render.accept(renderStack, if (keybind == null) text else "")
        if (keybind != null) {
            SkysoftErrorBoundary.run("Menu Keybind rendering") { MenuKeybinds.renderKeybind(context, font, keybind, x, y) }
        }
    }

    @JvmStatic
    fun renderSlotItem(
        suppressionBoundary: String,
        rarityBoundary: String,
        screen: AbstractContainerScreen<*>,
        context: GuiGraphicsExtractor,
        slot: Slot?,
        stack: ItemStack,
        render: Consumer<ItemStack>,
    ) {
        if (!shouldRenderSlotItem(suppressionBoundary, screen, slot)) return
        val renderStack = containerRenderStack(screen, slot, stack) ?: return
        SkysoftErrorBoundary.run("Container Search background") {
            if (slot != null) ContainerSearchHighlighter.renderBackground(context, slot)
        }
        val rarityStack = SkysoftErrorBoundary.value("Animated dye armor rarity", renderStack) {
            AnimatedDyeArmorCache.displayStack(screen, slot, renderStack)
        }
        SkysoftErrorBoundary.aroundUnit(rarityBoundary, { render.accept(renderStack) }) { renderItem ->
            RarityHighlightRenderer.renderContainerItem(rarityStack, renderItem)
        }
    }

    private fun shouldRenderSlotItem(
        boundary: String,
        screen: AbstractContainerScreen<*>,
        slot: Slot?,
    ): Boolean = SkysoftErrorBoundary.value(boundary, true) {
        InventoryEquipment.isEquipmentSlot(slot) || !SmoothSwapping.shouldSuppressSlot(screen, slot)
    }

    private fun containerRenderStack(screen: AbstractContainerScreen<*>, slot: Slot?, stack: ItemStack): ItemStack? {
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
}
