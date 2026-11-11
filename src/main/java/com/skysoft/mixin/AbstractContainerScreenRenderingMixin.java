package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.skysoft.features.misc.InventoryCraftingHider;
import com.skysoft.integration.ContainerItemRenderHooks;
import com.skysoft.integration.ContainerRenderHooks;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenRenderingMixin {
    @Inject(method = "extractContents", at = @At("HEAD"))
    private void skysoftBeginContainerFrame(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ContainerRenderHooks.beginContents(skysoftScreen(), context);
    }

    @Inject(method = "extractSlots", at = @At("HEAD"), cancellable = true)
    private void skysoftSuppressContainerSlots(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        if (ContainerRenderHooks.shouldSuppressSlots(skysoftScreen())) ci.cancel();
    }

    @Inject(method = "extractContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractLabels(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V", shift = At.Shift.AFTER))
    private void skysoftRenderAfterContainerLabels(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ContainerRenderHooks.renderAfterLabels(skysoftScreen(), context);
    }

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void skysoftRenderContainerTail(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ContainerRenderHooks.renderContentsTail(skysoftScreen(), context, mouseX, mouseY);
    }

    @Inject(method = "extractContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlotHighlightFront(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
    private void skysoftRenderEquipmentSlots(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ContainerRenderHooks.renderEquipment(skysoftScreen(), context, mouseX, mouseY);
    }

    @Inject(method = "extractSlotHighlightBack", at = @At("HEAD"), cancellable = true)
    private void skysoftHideInventoryCraftingHighlightBack(GuiGraphicsExtractor context, CallbackInfo ci) {
        if (skysoftShouldHideInventoryCraftingHighlight()) ci.cancel();
    }

    @Inject(method = "extractSlotHighlightFront", at = @At("HEAD"), cancellable = true)
    private void skysoftHideInventoryCraftingHighlightFront(GuiGraphicsExtractor context, CallbackInfo ci) {
        if (skysoftShouldHideInventoryCraftingHighlight()) ci.cancel();
    }

    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void skysoftRenderSlotBackgrounds(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ContainerRenderHooks.renderSlotBackgrounds(skysoftScreen(), context, slot);
    }

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void skysoftRenderSlotOverlays(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ContainerRenderHooks.renderSlotOverlays(skysoftScreen(), context, slot);
    }

    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void skysoftRenderContainerItem(GuiGraphicsExtractor context, ItemStack stack, int x, int y, int seed, Operation<Void> original, @Local(argsOnly = true) Slot slot) {
        ContainerItemRenderHooks.renderSlotItem(
            "Smooth Swapping item suppression",
            "Rarity Highlight item rendering",
            skysoftScreen(),
            context,
            slot,
            stack,
            renderStack -> original.call(context, renderStack, x, y, seed)
        );
    }

    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fakeItem(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void skysoftRenderContainerFakeItem(GuiGraphicsExtractor context, ItemStack stack, int x, int y, int seed, Operation<Void> original, @Local(argsOnly = true) Slot slot) {
        ContainerItemRenderHooks.renderSlotItem(
            "Smooth Swapping fake item suppression",
            "Rarity Highlight fake item rendering",
            skysoftScreen(),
            context,
            slot,
            stack,
            renderStack -> original.call(context, renderStack, x, y, seed)
        );
    }

    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void skysoftRenderContainerItemDecorations(GuiGraphicsExtractor context, Font font, ItemStack stack, int x, int y, String text, Operation<Void> original, @Local(argsOnly = true) Slot slot) {
        ContainerItemRenderHooks.renderSlotItemDecorations(
            skysoftScreen(),
            context,
            font,
            slot,
            stack,
            x,
            y,
            text,
            (renderStack, renderText) -> original.call(context, font, renderStack, x, y, renderText)
        );
    }

    @Unique
    private boolean skysoftShouldHideInventoryCraftingHighlight() {
        return MixinErrorBoundary.value(
            "Inventory crafting highlight",
            false,
            () -> InventoryCraftingHider.shouldHideHighlight(
                skysoftScreen(),
                ((AbstractContainerScreenAccessor) (Object) this).skysoftGetHoveredSlot()
            )
        );
    }

    @Unique
    private AbstractContainerScreen<?> skysoftScreen() {
        return (AbstractContainerScreen<?>) (Object) this;
    }
}
