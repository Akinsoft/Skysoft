package com.skysoft.integration

import com.skysoft.features.bazaar.BazaarTracker
import com.skysoft.features.inventory.ContainerSearchHighlighter
import com.skysoft.features.inventory.ExperimentationTableHelper
import com.skysoft.features.inventory.InventoryButtonManager
import com.skysoft.features.inventory.InventoryEquipment
import com.skysoft.features.inventory.ItemProtectionManager
import com.skysoft.features.inventory.RarityHighlightRenderer
import com.skysoft.features.inventory.SlotBindingManager
import com.skysoft.features.inventory.SlotLockManager
import com.skysoft.features.inventory.SmoothSwapping
import com.skysoft.features.inventory.StorageOverlayController
import com.skysoft.features.inventory.itemlist.ItemListController
import com.skysoft.features.pets.ActivePetHighlighter
import com.skysoft.features.ravengard.CrownValueOverlay
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.Slot

object ContainerRenderHooks {
    @JvmStatic
    fun beginContents(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor) {
        SkysoftErrorBoundary.run("Rarity Highlight frame", RarityHighlightRenderer::beginFrame)
        SkysoftErrorBoundary.run("Experimentation Table helper frame") {
            ExperimentationTableHelper.beginFrame(screen)
        }
        SkysoftErrorBoundary.run("Smooth Swapping render frame") {
            SmoothSwapping.beginFrame(screen)
        }
        SkysoftErrorBoundary.run("Inventory Equipment background rendering") {
            InventoryEquipment.renderBackground(screen, context)
        }
    }

    @JvmStatic
    fun shouldSuppressSlots(screen: AbstractContainerScreen<*>): Boolean =
        SkysoftErrorBoundary.value("Storage Overlay slot rendering", false) {
            StorageOverlayController.isActive(screen)
        }

    @JvmStatic
    fun renderAfterLabels(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor) {
        SkysoftErrorBoundary.run("Rarity Highlight backgrounds") {
            RarityHighlightRenderer.renderContainerBackgrounds(context, screen)
        }
    }

    @JvmStatic
    fun renderContentsTail(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        SkysoftErrorBoundary.run("Smooth Swapping rendering") {
            SmoothSwapping.render(screen, context)
        }
        SkysoftErrorBoundary.run("Slot Binding rendering") {
            SlotBindingManager.render(screen, context, mouseX, mouseY)
        }
        SkysoftErrorBoundary.run("Slot Lock render frame", SlotLockManager::beginFrame)
        SkysoftErrorBoundary.run("Item Protection render frame", ItemProtectionManager::beginFrame)
        SkysoftErrorBoundary.run("Inventory Button rendering") {
            InventoryButtonManager.render(screen, context, mouseX, mouseY)
        }
        SkysoftErrorBoundary.run("Item List rendering") {
            ItemListController.render(screen, context, mouseX, mouseY)
        }
    }

    @JvmStatic
    fun renderEquipment(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        SkysoftErrorBoundary.run("Inventory Equipment rendering") {
            InventoryEquipment.render(screen, context, mouseX, mouseY)
        }
    }

    @JvmStatic
    fun renderSlotBackgrounds(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor, slot: Slot) {
        SkysoftErrorBoundary.run("Container Search highlighting") {
            ContainerSearchHighlighter.renderBackground(context, slot)
        }
        SkysoftErrorBoundary.run("Active Pet highlighting") {
            ActivePetHighlighter.renderBackground(screen, context, slot)
        }
        SkysoftErrorBoundary.run("Bazaar Tracker slot background") {
            BazaarTracker.renderSlotIndicatorBackground(screen, context, slot)
        }
    }

    @JvmStatic
    fun renderSlotOverlays(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor, slot: Slot) {
        SkysoftErrorBoundary.run("Active Pet highlight outline") {
            ActivePetHighlighter.renderOutline(screen, context, slot)
        }
        SkysoftErrorBoundary.run("Bazaar Tracker slot overlay") {
            BazaarTracker.renderSlotIndicatorOverlay(screen, context, slot)
        }
        SkysoftErrorBoundary.run("Experimentation Table helper slot") {
            ExperimentationTableHelper.renderSlot(screen, context, slot)
        }
        SkysoftErrorBoundary.run("Slot Lock overlay") {
            SlotLockManager.renderSlotOverlay(context, slot)
        }
        SkysoftErrorBoundary.run("Item Protection marker") {
            ItemProtectionManager.renderProtectedMarker(context, slot)
        }
        SkysoftErrorBoundary.run("Ravengard Crown Value overlay") {
            CrownValueOverlay.render(context, slot)
        }
    }
}
