package com.skysoft.mixin

import com.skysoft.features.bazaar.BazaarTracker
import com.skysoft.features.inventory.ContainerSearchHighlighter
import com.skysoft.features.inventory.InventoryButtonManager
import com.skysoft.features.inventory.InventoryEquipment
import com.skysoft.features.inventory.ItemProtectionManager
import com.skysoft.features.inventory.RarityHighlightRenderer
import com.skysoft.features.inventory.SlotBindingManager
import com.skysoft.features.inventory.SlotLockManager
import com.skysoft.features.inventory.SmoothSwapping
import com.skysoft.features.inventory.itemlist.ItemListController
import com.skysoft.features.misc.PlayerHeadSkinFix
import com.skysoft.features.pets.ActivePetHighlighter
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.Redirect
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(AbstractContainerScreen::class)
abstract class AbstractContainerScreenRenderingMixin {
    @field:Unique
    private var skysoftSmoothSwappingSlot: Slot? = null

    @Inject(method = ["extractContents"], at = [At("HEAD")])
    protected fun skysoftBeginSmoothSwappingFrame(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
        ci: CallbackInfo,
    ) {
        val screen = this as AbstractContainerScreen<*>
        SkysoftErrorBoundary.run("Rarity Highlight frame", RarityHighlightRenderer::beginFrame)
        SkysoftErrorBoundary.run("Smooth Swapping render frame") { SmoothSwapping.beginFrame(screen) }
        SkysoftErrorBoundary.run("Inventory Equipment background rendering") {
            InventoryEquipment.renderBackground(screen, context)
        }
    }

    @Inject(method = ["extractContents"], at = [At("TAIL")])
    protected fun skysoftRenderInventoryButtons(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
        ci: CallbackInfo,
    ) {
        val screen = this as AbstractContainerScreen<*>
        SkysoftErrorBoundary.run("Smooth Swapping rendering") { SmoothSwapping.render(screen, context) }
        SkysoftErrorBoundary.run("Slot Binding rendering") { SlotBindingManager.render(screen, context, mouseX, mouseY) }
        SkysoftErrorBoundary.run("Slot Lock render frame", SlotLockManager::beginFrame)
        SkysoftErrorBoundary.run("Item Protection render frame", ItemProtectionManager::beginFrame)
        SkysoftErrorBoundary.run("Inventory Button rendering") {
            InventoryButtonManager.render(screen, context, mouseX, mouseY)
        }
        SkysoftErrorBoundary.run("Item List rendering") { ItemListController.render(screen, context, mouseX, mouseY) }
    }

    @Inject(
        method = ["extractContents"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;" +
                    "extractSlotHighlightFront(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            ),
        ],
    )
    protected fun skysoftRenderInventoryEquipmentSlots(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
        ci: CallbackInfo,
    ) {
        SkysoftErrorBoundary.run("Inventory Equipment rendering") {
            InventoryEquipment.render(this as AbstractContainerScreen<*>, context, mouseX, mouseY)
        }
    }

    @Inject(method = ["extractSlot"], at = [At("HEAD")])
    protected fun skysoftRenderActivePetHighlightBackground(
        context: GuiGraphicsExtractor,
        slot: Slot,
        mouseX: Int,
        mouseY: Int,
        ci: CallbackInfo,
    ) {
        val screen = this as AbstractContainerScreen<*>
        SkysoftErrorBoundary.run("Rarity Highlight background") { RarityHighlightRenderer.renderBackground(context, slot) }
        SkysoftErrorBoundary.run("Container Search highlighting") {
            ContainerSearchHighlighter.renderBackground(screen, context, slot)
        }
        SkysoftErrorBoundary.run("Active Pet highlighting") { ActivePetHighlighter.renderBackground(screen, context, slot) }
        SkysoftErrorBoundary.run("Bazaar Tracker slot background") {
            BazaarTracker.renderSlotIndicatorBackground(screen, context, slot)
        }
    }

    @Inject(method = ["extractSlot"], at = [At("TAIL")])
    protected fun skysoftRenderActivePetHighlightOutline(
        context: GuiGraphicsExtractor,
        slot: Slot,
        mouseX: Int,
        mouseY: Int,
        ci: CallbackInfo,
    ) {
        val screen = this as AbstractContainerScreen<*>
        SkysoftErrorBoundary.run("Active Pet highlight outline") { ActivePetHighlighter.renderOutline(screen, context, slot) }
        SkysoftErrorBoundary.run("Bazaar Tracker slot overlay") {
            BazaarTracker.renderSlotIndicatorOverlay(screen, context, slot)
        }
        SkysoftErrorBoundary.run("Slot Lock overlay") { SlotLockManager.renderSlotOverlay(context, slot) }
        SkysoftErrorBoundary.run("Item Protection marker") { ItemProtectionManager.renderProtectedMarker(context, slot) }
    }

    @Inject(method = ["extractSlot"], at = [At("HEAD")])
    protected fun skysoftRememberSmoothSwappingSlot(
        context: GuiGraphicsExtractor,
        slot: Slot,
        mouseX: Int,
        mouseY: Int,
        ci: CallbackInfo,
    ) {
        skysoftSmoothSwappingSlot = slot
    }

    @Inject(method = ["extractSlot"], at = [At("RETURN")])
    protected fun skysoftClearSmoothSwappingSlot(
        context: GuiGraphicsExtractor,
        slot: Slot,
        mouseX: Int,
        mouseY: Int,
        ci: CallbackInfo,
    ) {
        skysoftSmoothSwappingSlot = null
    }

    @Redirect(
        method = ["extractSlot"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;" +
                "item(Lnet/minecraft/world/item/ItemStack;III)V",
        ),
    )
    protected fun skysoftSuppressSmoothSwappingItem(
        context: GuiGraphicsExtractor,
        stack: ItemStack,
        x: Int,
        y: Int,
        seed: Int,
    ) {
        val shouldRender = SkysoftErrorBoundary.value("Smooth Swapping item suppression", true) {
            InventoryEquipment.isEquipmentSlot(skysoftSmoothSwappingSlot) ||
                !SmoothSwapping.shouldSuppressSlot(this as AbstractContainerScreen<*>, skysoftSmoothSwappingSlot)
        }
        if (!shouldRender) return
        val renderStack = SkysoftErrorBoundary.value<ItemStack?>("Player Head Skin inventory item", stack) {
            PlayerHeadSkinFix.inventoryStack(skysoftSmoothSwappingSlot, stack)
        } ?: return
        renderItemWithRarity("Rarity Highlight item rendering", stack) { context.item(renderStack, x, y, seed) }
    }

    @Redirect(
        method = ["extractSlot"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;" +
                "fakeItem(Lnet/minecraft/world/item/ItemStack;III)V",
        ),
    )
    protected fun skysoftSuppressSmoothSwappingFakeItem(
        context: GuiGraphicsExtractor,
        stack: ItemStack,
        x: Int,
        y: Int,
        seed: Int,
    ) {
        val shouldRender = SkysoftErrorBoundary.value("Smooth Swapping fake item suppression", true) {
            InventoryEquipment.isEquipmentSlot(skysoftSmoothSwappingSlot) ||
                !SmoothSwapping.shouldSuppressSlot(this as AbstractContainerScreen<*>, skysoftSmoothSwappingSlot)
        }
        if (!shouldRender) return
        val renderStack = SkysoftErrorBoundary.value<ItemStack?>("Player Head Skin inventory item", stack) {
            PlayerHeadSkinFix.inventoryStack(skysoftSmoothSwappingSlot, stack)
        } ?: return
        renderItemWithRarity("Rarity Highlight fake item rendering", stack) {
            context.fakeItem(renderStack, x, y, seed)
        }
    }

    @Redirect(
        method = ["extractSlot"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;" +
                "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;" +
                "IILjava/lang/String;)V",
        ),
    )
    protected fun skysoftSuppressSmoothSwappingItemDecorations(
        context: GuiGraphicsExtractor,
        font: Font,
        stack: ItemStack,
        x: Int,
        y: Int,
        text: String?,
    ) {
        val shouldRender = SkysoftErrorBoundary.value("Smooth Swapping item decoration suppression", true) {
            val isVisible =
                InventoryEquipment.isEquipmentSlot(skysoftSmoothSwappingSlot) ||
                    !SmoothSwapping.shouldSuppressSlot(
                        this as AbstractContainerScreen<*>,
                        skysoftSmoothSwappingSlot,
                    )
            isVisible && PlayerHeadSkinFix.inventoryStack(skysoftSmoothSwappingSlot, stack) != null
        }
        if (shouldRender) context.itemDecorations(font, stack, x, y, text)
    }

    @Unique
    private fun renderItemWithRarity(boundary: String, stack: ItemStack, render: () -> Unit) {
        SkysoftErrorBoundary.aroundUnit(boundary, render) { renderItem ->
            RarityHighlightRenderer.renderItem(stack, renderItem)
        }
    }
}
