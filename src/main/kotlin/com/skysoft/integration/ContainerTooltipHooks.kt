package com.skysoft.integration

import com.skysoft.features.inventory.AnimatedDyeArmorCache
import com.skysoft.features.inventory.ExperimentationTableHelper
import com.skysoft.features.inventory.MinisterCalendarTooltip
import com.skysoft.features.inventory.SlotBindingManager
import com.skysoft.features.inventory.StorageOverlayController
import com.skysoft.features.ravengard.RavengardItemComparisonTooltip
import com.skysoft.gui.tooltip.AdjacentTooltipRenderer
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

object ContainerTooltipHooks {
    @JvmStatic
    fun shouldSuppressTooltip(screen: AbstractContainerScreen<*>): Boolean {
        AdjacentTooltipRenderer.clear()
        return SkysoftErrorBoundary.value("Slot Binding tooltip suppression", false) {
            SlotBindingManager.shouldSuppressRegularTooltips(screen)
        }
    }

    @JvmStatic
    fun prepareTooltip(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor) {
        SkysoftErrorBoundary.run("Minister in Calendar tooltip preparation") {
            MinisterCalendarTooltip.prepare(screen, context)
        }
        SkysoftErrorBoundary.run("Ravengard item comparison tooltip preparation") {
            RavengardItemComparisonTooltip.prepare(screen, context)
        }
    }

    @JvmStatic
    fun tooltipStack(screen: AbstractContainerScreen<*>, slot: Slot, stack: ItemStack): ItemStack {
        val dyeStack = SkysoftErrorBoundary.value("Animated dye armor tooltip", stack) {
            AnimatedDyeArmorCache.displayStack(screen, slot, stack)
        }
        return SkysoftErrorBoundary.value("Experimentation Table remembered tooltip", dyeStack) {
            ExperimentationTableHelper.displayStack(screen, slot, dyeStack)
        }
    }

    @JvmStatic
    fun shouldSuppressLabels(screen: AbstractContainerScreen<*>): Boolean =
        SkysoftErrorBoundary.value("Storage Overlay label suppression", false) {
            StorageOverlayController.shouldSuppressContainerLabels(screen)
        }
}
