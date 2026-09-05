package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.integration.ContainerItemRenderHooks;
import com.skysoft.integration.ContainerRenderHooks;
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
    @Unique private Slot skysoftCurrentSlot;

    @Inject(method = "extractContents", at = @At("HEAD"))
    protected void skysoftBeginContainerFrame(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ContainerRenderHooks.beginContents((AbstractContainerScreen<?>) (Object) this, context);
    }

    @Inject(method = "extractSlots", at = @At("HEAD"), cancellable = true)
    protected void skysoftSuppressContainerSlots(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        if (ContainerRenderHooks.shouldSuppressSlots((AbstractContainerScreen<?>) (Object) this)) ci.cancel();
    }

    @Inject(method = "extractContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractLabels(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V", shift = At.Shift.AFTER))
    protected void skysoftRenderAfterContainerLabels(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ContainerRenderHooks.renderAfterLabels((AbstractContainerScreen<?>) (Object) this, context);
    }

    @Inject(method = "extractContents", at = @At("TAIL"))
    protected void skysoftRenderContainerTail(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ContainerRenderHooks.renderContentsTail((AbstractContainerScreen<?>) (Object) this, context, mouseX, mouseY);
    }

    @Inject(method = "extractContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlotHighlightFront(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
    protected void skysoftRenderEquipmentSlots(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ContainerRenderHooks.renderEquipment((AbstractContainerScreen<?>) (Object) this, context, mouseX, mouseY);
    }

    @Inject(method = "extractSlot", at = @At("HEAD"))
    protected void skysoftRenderSlotBackgrounds(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ContainerRenderHooks.renderSlotBackgrounds((AbstractContainerScreen<?>) (Object) this, context, slot);
    }

    @Inject(method = "extractSlot", at = @At("TAIL"))
    protected void skysoftRenderSlotOverlays(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ContainerRenderHooks.renderSlotOverlays((AbstractContainerScreen<?>) (Object) this, context, slot);
    }

    @Inject(method = "extractSlot", at = @At("HEAD"))
    protected void skysoftRememberCurrentSlot(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        skysoftCurrentSlot = slot;
    }

    @Inject(method = "extractSlot", at = @At("RETURN"))
    protected void skysoftClearCurrentSlot(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        skysoftCurrentSlot = null;
    }

    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"))
    protected void skysoftRenderContainerItem(GuiGraphicsExtractor context, ItemStack stack, int x, int y, int seed, Operation<Void> original) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (!ContainerItemRenderHooks.shouldRenderSlotItem("Smooth Swapping item suppression", screen, skysoftCurrentSlot)) return;
        ItemStack renderStack = ContainerItemRenderHooks.containerRenderStack(screen, skysoftCurrentSlot, stack);
        if (renderStack != null) {
            ContainerItemRenderHooks.renderItemWithRarity(
                "Rarity Highlight item rendering",
                screen,
                context,
                skysoftCurrentSlot,
                renderStack,
                () -> original.call(context, renderStack, x, y, seed)
            );
        }
    }

    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fakeItem(Lnet/minecraft/world/item/ItemStack;III)V"))
    protected void skysoftRenderContainerFakeItem(GuiGraphicsExtractor context, ItemStack stack, int x, int y, int seed, Operation<Void> original) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (!ContainerItemRenderHooks.shouldRenderSlotItem("Smooth Swapping fake item suppression", screen, skysoftCurrentSlot)) return;
        ItemStack renderStack = ContainerItemRenderHooks.containerRenderStack(screen, skysoftCurrentSlot, stack);
        if (renderStack != null) {
            ContainerItemRenderHooks.renderItemWithRarity(
                "Rarity Highlight fake item rendering",
                screen,
                context,
                skysoftCurrentSlot,
                renderStack,
                () -> original.call(context, renderStack, x, y, seed)
            );
        }
    }

    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    protected void skysoftRenderContainerItemDecorations(GuiGraphicsExtractor context, Font font, ItemStack stack, int x, int y, String text, Operation<Void> original) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (!ContainerItemRenderHooks.shouldRenderSlotItem(
            "Smooth Swapping item decoration suppression",
            screen,
            skysoftCurrentSlot
        )) return;
        ItemStack renderStack = ContainerItemRenderHooks.containerRenderStack(screen, skysoftCurrentSlot, stack);
        if (renderStack != null) original.call(context, font, renderStack, x, y, text);
    }
}
