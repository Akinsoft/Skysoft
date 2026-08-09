package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.skysoft.features.bazaar.BazaarTracker;
import com.skysoft.features.inventory.AnimatedDyeArmorCache;
import com.skysoft.features.inventory.ContainerSearchHighlighter;
import com.skysoft.features.inventory.ExperimentationTableHelper;
import com.skysoft.features.inventory.InventoryButtonManager;
import com.skysoft.features.inventory.InventoryEquipment;
import com.skysoft.features.inventory.ItemProtectionManager;
import com.skysoft.features.inventory.RarityHighlightRenderer;
import com.skysoft.features.inventory.SlotBindingManager;
import com.skysoft.features.inventory.SlotLockManager;
import com.skysoft.features.inventory.SmoothSwapping;
import com.skysoft.features.inventory.StorageOverlayController;
import com.skysoft.features.inventory.itemlist.ItemListController;
import com.skysoft.features.misc.PlayerHeadSkinFix;
import com.skysoft.features.pets.ActivePetHighlighter;
import com.skysoft.features.ravengard.CrownValueOverlay;
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
    @Unique private Slot skysoftSmoothSwappingSlot;

    @Inject(method = "extractContents", at = @At("HEAD"))
    protected void skysoftBeginSmoothSwappingFrame(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        MixinErrorBoundary.run("Rarity Highlight frame", RarityHighlightRenderer::beginFrame);
        MixinErrorBoundary.run("Experimentation Table helper frame", () -> ExperimentationTableHelper.INSTANCE.beginFrame(screen));
        MixinErrorBoundary.run("Smooth Swapping render frame", () -> SmoothSwapping.INSTANCE.beginFrame(screen));
        MixinErrorBoundary.run("Inventory Equipment background rendering", () -> InventoryEquipment.INSTANCE.renderBackground(screen, context));
    }

    @Inject(method = "extractSlots", at = @At("HEAD"), cancellable = true)
    protected void skysoftSuppressStorageOverlaySlots(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        boolean suppress = MixinErrorBoundary.value("Storage Overlay slot rendering", false,
            () -> StorageOverlayController.INSTANCE.isActive((AbstractContainerScreen<?>) (Object) this));
        if (suppress) ci.cancel();
    }

    @Inject(method = "extractContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractLabels(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V", shift = At.Shift.AFTER))
    protected void skysoftRenderRarityHighlightBackgrounds(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        MixinErrorBoundary.run("Rarity Highlight backgrounds", () -> RarityHighlightRenderer.renderContainerBackgrounds(context, screen));
    }

    @Inject(method = "extractContents", at = @At("TAIL"))
    protected void skysoftRenderInventoryButtons(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        MixinErrorBoundary.run("Smooth Swapping rendering", () -> SmoothSwapping.INSTANCE.render(screen, context));
        MixinErrorBoundary.run("Slot Binding rendering", () -> SlotBindingManager.INSTANCE.render(screen, context, mouseX, mouseY));
        MixinErrorBoundary.run("Slot Lock render frame", SlotLockManager::beginFrame);
        MixinErrorBoundary.run("Item Protection render frame", ItemProtectionManager::beginFrame);
        MixinErrorBoundary.run("Inventory Button rendering", () -> InventoryButtonManager.INSTANCE.render(screen, context, mouseX, mouseY));
        MixinErrorBoundary.run("Item List rendering", () -> ItemListController.INSTANCE.render(screen, context, mouseX, mouseY));
    }

    @Inject(method = "extractContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlotHighlightFront(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
    protected void skysoftRenderInventoryEquipmentSlots(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MixinErrorBoundary.run("Inventory Equipment rendering", () -> InventoryEquipment.INSTANCE.render((AbstractContainerScreen<?>) (Object) this, context, mouseX, mouseY));
    }

    @Inject(method = "extractSlot", at = @At("HEAD"))
    protected void skysoftRenderSlotBackgrounds(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        MixinErrorBoundary.run("Container Search highlighting", () -> ContainerSearchHighlighter.renderBackground(context, slot));
        MixinErrorBoundary.run("Active Pet highlighting", () -> ActivePetHighlighter.INSTANCE.renderBackground(screen, context, slot));
        MixinErrorBoundary.run("Bazaar Tracker slot background", () -> BazaarTracker.INSTANCE.renderSlotIndicatorBackground(screen, context, slot));
    }

    @Inject(method = "extractSlot", at = @At("TAIL"))
    protected void skysoftRenderActivePetHighlightOutline(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        MixinErrorBoundary.run("Active Pet highlight outline", () -> ActivePetHighlighter.INSTANCE.renderOutline(screen, context, slot));
        MixinErrorBoundary.run("Bazaar Tracker slot overlay", () -> BazaarTracker.INSTANCE.renderSlotIndicatorOverlay(screen, context, slot));
        MixinErrorBoundary.run("Experimentation Table helper slot", () -> ExperimentationTableHelper.INSTANCE.renderSlot(screen, context, slot));
        MixinErrorBoundary.run("Slot Lock overlay", () -> SlotLockManager.INSTANCE.renderSlotOverlay(context, slot));
        MixinErrorBoundary.run("Item Protection marker", () -> ItemProtectionManager.INSTANCE.renderProtectedMarker(context, slot));
        MixinErrorBoundary.run("Ravengard Crown Value overlay", () -> CrownValueOverlay.render(context, slot));
    }

    @Inject(method = "extractSlot", at = @At("HEAD"))
    protected void skysoftRememberSmoothSwappingSlot(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) { skysoftSmoothSwappingSlot = slot; }

    @Inject(method = "extractSlot", at = @At("RETURN"))
    protected void skysoftClearSmoothSwappingSlot(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) { skysoftSmoothSwappingSlot = null; }

    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"))
    protected void skysoftSuppressSmoothSwappingItem(GuiGraphicsExtractor context, ItemStack stack, int x, int y, int seed, Operation<Void> original) {
        boolean render = MixinErrorBoundary.value("Smooth Swapping item suppression", true,
            () -> InventoryEquipment.INSTANCE.isEquipmentSlot(skysoftSmoothSwappingSlot) || !SmoothSwapping.INSTANCE.shouldSuppressSlot((AbstractContainerScreen<?>) (Object) this, skysoftSmoothSwappingSlot));
        if (!render) return;
        ItemStack renderStack = skysoftContainerRenderStack(stack);
        if (renderStack != null) renderItemWithRarity("Rarity Highlight item rendering", renderStack, () -> original.call(context, renderStack, x, y, seed));
    }

    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fakeItem(Lnet/minecraft/world/item/ItemStack;III)V"))
    protected void skysoftSuppressSmoothSwappingFakeItem(GuiGraphicsExtractor context, ItemStack stack, int x, int y, int seed, Operation<Void> original) {
        boolean render = MixinErrorBoundary.value("Smooth Swapping fake item suppression", true,
            () -> InventoryEquipment.INSTANCE.isEquipmentSlot(skysoftSmoothSwappingSlot) || !SmoothSwapping.INSTANCE.shouldSuppressSlot((AbstractContainerScreen<?>) (Object) this, skysoftSmoothSwappingSlot));
        if (!render) return;
        ItemStack renderStack = skysoftContainerRenderStack(stack);
        if (renderStack != null) renderItemWithRarity("Rarity Highlight fake item rendering", renderStack, () -> original.call(context, renderStack, x, y, seed));
    }

    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    protected void skysoftSuppressSmoothSwappingItemDecorations(GuiGraphicsExtractor context, Font font, ItemStack stack, int x, int y, String text, Operation<Void> original) {
        boolean render = MixinErrorBoundary.value("Smooth Swapping item decoration suppression", true,
            () -> InventoryEquipment.INSTANCE.isEquipmentSlot(skysoftSmoothSwappingSlot) || !SmoothSwapping.INSTANCE.shouldSuppressSlot((AbstractContainerScreen<?>) (Object) this, skysoftSmoothSwappingSlot));
        if (!render) return;
        ItemStack renderStack = skysoftContainerRenderStack(stack);
        if (renderStack != null) original.call(context, font, renderStack, x, y, text);
    }

    @Unique
    private ItemStack skysoftContainerRenderStack(ItemStack stack) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        ItemStack experimentationStack = MixinErrorBoundary.value(
            "Experimentation Table remembered item",
            stack,
            () -> ExperimentationTableHelper.INSTANCE.displayStack(screen, skysoftSmoothSwappingSlot, stack)
        );
        return MixinErrorBoundary.value(
            "Player Head Skin inventory item",
            experimentationStack,
            () -> PlayerHeadSkinFix.INSTANCE.inventoryStack(skysoftSmoothSwappingSlot, experimentationStack)
        );
    }

    @Unique
    private void renderItemWithRarity(String boundary, ItemStack stack, Runnable render) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        ItemStack rarityStack = MixinErrorBoundary.value(
            "Animated dye armor rarity",
            stack,
            () -> AnimatedDyeArmorCache.displayStack(screen, skysoftSmoothSwappingSlot, stack)
        );
        MixinErrorBoundary.aroundUnit(boundary, render, renderItem -> RarityHighlightRenderer.renderContainerItem(rarityStack, () -> { renderItem.run(); return kotlin.Unit.INSTANCE; }));
    }
}
