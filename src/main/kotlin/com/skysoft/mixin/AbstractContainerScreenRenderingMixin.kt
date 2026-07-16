package com.skysoft.mixin

import com.skysoft.features.bazaar.BazaarTracker
import com.skysoft.features.inventory.ContainerSearchHighlighter
import com.skysoft.features.inventory.InventoryButtonManager
import com.skysoft.features.inventory.InventoryEquipment
import com.skysoft.features.inventory.ItemProtectionManager
import com.skysoft.features.inventory.SlotBindingManager
import com.skysoft.features.inventory.SlotLockManager
import com.skysoft.features.inventory.SmoothSwapping
import com.skysoft.features.inventory.itemlist.ItemListController
import com.skysoft.features.misc.PlayerHeadSkinFix
import com.skysoft.features.pets.ActivePetHighlighter
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
        SmoothSwapping.beginFrame(this as AbstractContainerScreen<*>)
        InventoryEquipment.renderBackground(this as AbstractContainerScreen<*>, context)
    }

    @Inject(method = ["extractContents"], at = [At("TAIL")])
    protected fun skysoftRenderInventoryButtons(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
        ci: CallbackInfo,
    ) {
        SmoothSwapping.render(this as AbstractContainerScreen<*>, context)
        SlotBindingManager.render(this as AbstractContainerScreen<*>, context, mouseX, mouseY)
        SlotLockManager.beginFrame()
        ItemProtectionManager.beginFrame()
        InventoryButtonManager.render(this as AbstractContainerScreen<*>, context, mouseX, mouseY)
        ItemListController.render(this as AbstractContainerScreen<*>, context, mouseX, mouseY)
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
        InventoryEquipment.render(this as AbstractContainerScreen<*>, context, mouseX, mouseY)
    }

    @Inject(method = ["extractSlot"], at = [At("HEAD")])
    protected fun skysoftRenderActivePetHighlightBackground(
        context: GuiGraphicsExtractor,
        slot: Slot,
        mouseX: Int,
        mouseY: Int,
        ci: CallbackInfo,
    ) {
        ContainerSearchHighlighter.renderBackground(this as AbstractContainerScreen<*>, context, slot)
        ActivePetHighlighter.renderBackground(this as AbstractContainerScreen<*>, context, slot)
        BazaarTracker.renderSlotIndicatorBackground(this as AbstractContainerScreen<*>, context, slot)
    }

    @Inject(method = ["extractSlot"], at = [At("TAIL")])
    protected fun skysoftRenderActivePetHighlightOutline(
        context: GuiGraphicsExtractor,
        slot: Slot,
        mouseX: Int,
        mouseY: Int,
        ci: CallbackInfo,
    ) {
        ActivePetHighlighter.renderOutline(this as AbstractContainerScreen<*>, context, slot)
        BazaarTracker.renderSlotIndicatorOverlay(this as AbstractContainerScreen<*>, context, slot)
        SlotLockManager.renderSlotOverlay(context, slot)
        ItemProtectionManager.renderProtectedMarker(context, slot)
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
        if (
            InventoryEquipment.isEquipmentSlot(skysoftSmoothSwappingSlot) ||
            !SmoothSwapping.shouldSuppressSlot(
                this as AbstractContainerScreen<*>,
                skysoftSmoothSwappingSlot,
            )
        ) {
            val renderStack = PlayerHeadSkinFix.inventoryStack(skysoftSmoothSwappingSlot, stack)
            if (renderStack != null) {
                context.item(renderStack, x, y, seed)
            }
        }
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
        if (
            InventoryEquipment.isEquipmentSlot(skysoftSmoothSwappingSlot) ||
            !SmoothSwapping.shouldSuppressSlot(
                this as AbstractContainerScreen<*>,
                skysoftSmoothSwappingSlot,
            )
        ) {
            val renderStack = PlayerHeadSkinFix.inventoryStack(skysoftSmoothSwappingSlot, stack)
            if (renderStack != null) {
                context.fakeItem(renderStack, x, y, seed)
            }
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
        if (
            (
                InventoryEquipment.isEquipmentSlot(skysoftSmoothSwappingSlot) ||
                    !SmoothSwapping.shouldSuppressSlot(
                        this as AbstractContainerScreen<*>,
                        skysoftSmoothSwappingSlot,
                    )
                ) && PlayerHeadSkinFix.inventoryStack(skysoftSmoothSwappingSlot, stack) != null
        ) {
            context.itemDecorations(font, stack, x, y, text)
        }
    }
}
