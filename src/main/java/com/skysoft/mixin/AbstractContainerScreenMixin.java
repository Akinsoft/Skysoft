package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.features.bazaar.BazaarTracker;
import com.skysoft.features.inventory.InventoryButtonManager;
import com.skysoft.features.inventory.InventoryEquipment;
import com.skysoft.features.inventory.InventoryDropSelectionGuard;
import com.skysoft.features.inventory.SkyBlockMenuInventoryDropFix;
import com.skysoft.features.inventory.SlotBindingManager;
import com.skysoft.features.inventory.SlotLockManager;
import com.skysoft.features.inventory.SmoothSwapping;
import com.skysoft.features.inventory.StorageOverlayController;
import com.skysoft.features.inventory.itemlist.ItemListController;
import com.skysoft.features.misc.PlayerHeadSkinFix;
import com.skysoft.features.pets.ActivePetHighlighter;
import com.skysoft.features.pets.PetStorageService;
import com.skysoft.gui.tooltip.TooltipViewport;
import com.skysoft.utils.input.InputHandlingResult;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
    @Unique
    private Slot skysoft$smoothSwappingSlot;

    @Inject(method = "init()V", at = @At("TAIL"))
    private void skysoft$layoutStorageOverlay(CallbackInfo ci) {
        StorageOverlayController.layoutScreen((AbstractContainerScreen<?>) (Object) this);
        InventoryEquipment.layoutScreen((AbstractContainerScreen<?>) (Object) this);
    }

    @Inject(method = "extractContents", at = @At("HEAD"))
    private void skysoft$beginSmoothSwappingFrame(
        GuiGraphicsExtractor context,
        int mouseX,
        int mouseY,
        float delta,
        CallbackInfo ci
    ) {
        SmoothSwapping.beginFrame((AbstractContainerScreen<?>) (Object) this);
        InventoryEquipment.renderBackground((AbstractContainerScreen<?>) (Object) this, context);
    }

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void skysoft$renderInventoryButtons(
        GuiGraphicsExtractor context,
        int mouseX,
        int mouseY,
        float delta,
        CallbackInfo ci
    ) {
        SmoothSwapping.render((AbstractContainerScreen<?>) (Object) this, context);
        SlotBindingManager.render((AbstractContainerScreen<?>) (Object) this, context, mouseX, mouseY);
        SlotLockManager.beginFrame();
        InventoryButtonManager.render((AbstractContainerScreen<?>) (Object) this, context, mouseX, mouseY);
        ItemListController.render((AbstractContainerScreen<?>) (Object) this, context, mouseX, mouseY);
    }

    @Inject(
        method = "extractContents",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlotHighlightFront(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"
        )
    )
    private void skysoft$renderInventoryEquipmentSlots(
        GuiGraphicsExtractor context,
        int mouseX,
        int mouseY,
        float delta,
        CallbackInfo ci
    ) {
        InventoryEquipment.render((AbstractContainerScreen<?>) (Object) this, context, mouseX, mouseY);
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void skysoft$restoreInventoryEquipmentLayout(CallbackInfo ci) {
        InventoryEquipment.restoreScreen((AbstractContainerScreen<?>) (Object) this);
        SlotLockManager.clearInputState();
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void skysoft$suppressTooltipDuringSlotBinding(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        if (SlotBindingManager.shouldSuppressRegularTooltips((AbstractContainerScreen<?>) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void skysoft$suppressStorageOverlayLabels(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        if (StorageOverlayController.shouldSuppressContainerLabels((AbstractContainerScreen<?>) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void skysoft$clickStorageOverlay(
        MouseButtonEvent click,
        boolean doubled,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (ItemListController.handleMouseClick((AbstractContainerScreen<?>) (Object) this, click) == InputHandlingResult.CONSUMED) {
            cir.setReturnValue(true);
            return;
        }
        if (StorageOverlayController.handleMouseClick((AbstractContainerScreen<?>) (Object) this, click) == InputHandlingResult.CONSUMED) {
            cir.setReturnValue(true);
            return;
        }
        if (BazaarTracker.handleMouseClick((AbstractContainerScreen<?>) (Object) this, click) == InputHandlingResult.CONSUMED) {
            cir.setReturnValue(true);
            return;
        }
        if (InventoryEquipment.handleMouseClick((AbstractContainerScreen<?>) (Object) this, click) == InputHandlingResult.CONSUMED) {
            cir.setReturnValue(true);
            return;
        }
        if (InventoryButtonManager.handleMouseClick((AbstractContainerScreen<?>) (Object) this, click) == InputHandlingResult.CONSUMED) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void skysoft$releaseOverlayInput(
        MouseButtonEvent click,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (StorageOverlayController.handleMouseRelease(click) == InputHandlingResult.CONSUMED) {
            cir.setReturnValue(true);
            return;
        }
        if (InventoryButtonManager.handleMouseRelease((AbstractContainerScreen<?>) (Object) this, click) == InputHandlingResult.CONSUMED) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void skysoft$dragStorageOverlay(
        MouseButtonEvent click,
        double deltaX,
        double deltaY,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (StorageOverlayController.handleMouseDrag((AbstractContainerScreen<?>) (Object) this, click)
            == InputHandlingResult.CONSUMED
        ) {
            cir.setReturnValue(true);
        }
    }

    @WrapOperation(
        method = "mouseClicked",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"
        )
    )
    private boolean skysoft$suppressStorageOverlayWidgetClick(
        AbstractContainerScreen<?> screen,
        MouseButtonEvent click,
        boolean doubled,
        Operation<Boolean> original
    ) {
        if (StorageOverlayController.isActive(screen)) {
            return false;
        }
        return original.call(screen, click, doubled);
    }

    @WrapOperation(
        method = "mouseDragged",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z"
        )
    )
    private boolean skysoft$suppressStorageOverlayWidgetDrag(
        AbstractContainerScreen<?> screen,
        MouseButtonEvent click,
        double deltaX,
        double deltaY,
        Operation<Boolean> original
    ) {
        if (StorageOverlayController.isActive(screen)) {
            return false;
        }
        return original.call(screen, click, deltaX, deltaY);
    }

    @WrapOperation(
        method = "mouseDragged",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;shouldAddSlotToQuickCraft(Lnet/minecraft/world/inventory/Slot;Lnet/minecraft/world/item/ItemStack;)Z"
        )
    )
    private boolean skysoft$skipLockedQuickCraftSlot(
        AbstractContainerScreen<?> screen,
        Slot slot,
        ItemStack stack,
        Operation<Boolean> original
    ) {
        return SlotLockManager.canQuickCraftInto(slot) && original.call(screen, slot, stack);
    }

    @WrapOperation(
        method = "mouseReleased",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z"
        )
    )
    private boolean skysoft$suppressStorageOverlayWidgetRelease(
        AbstractContainerScreen<?> screen,
        MouseButtonEvent click,
        Operation<Boolean> original
    ) {
        if (StorageOverlayController.isActive(screen)) {
            return false;
        }
        return original.call(screen, click);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void skysoft$scrollStorageOverlay(
        double mouseX,
        double mouseY,
        double horizontalAmount,
        double verticalAmount,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (TooltipViewport.onMouseScroll(horizontalAmount, verticalAmount)) {
            cir.setReturnValue(true);
            return;
        }
        if (ItemListController.handleMouseScroll(
            (AbstractContainerScreen<?>) (Object) this,
            mouseX,
            mouseY,
            verticalAmount
        ) == InputHandlingResult.CONSUMED) {
            cir.setReturnValue(true);
            return;
        }
        if (StorageOverlayController.handleMouseScroll((AbstractContainerScreen<?>) (Object) this, mouseX, mouseY, verticalAmount)
            == InputHandlingResult.CONSUMED
        ) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void skysoft$keyStorageOverlay(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (ItemListController.handleKeyPress((AbstractContainerScreen<?>) (Object) this, event) == InputHandlingResult.CONSUMED) {
            cir.setReturnValue(true);
            return;
        }
        if (StorageOverlayController.handleKeyPress((AbstractContainerScreen<?>) (Object) this, event) == InputHandlingResult.CONSUMED) {
            cir.setReturnValue(true);
            return;
        }
        if (SlotLockManager.handleKeyPress((AbstractContainerScreen<?>) (Object) this, event) == InputHandlingResult.CONSUMED) {
            cir.setReturnValue(true);
        }
    }

    @WrapOperation(
        method = "slotClicked",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V"
        )
    )
    private void skysoft$preventSkyBlockMenuOpeningOnInventoryDrop(
        MultiPlayerGameMode gameMode,
        int containerId,
        int slotId,
        int button,
        ContainerInput action,
        Player player,
        Operation<Void> original
    ) {
        InventoryDropSelectionGuard guard = SkyBlockMenuInventoryDropFix.beginContainerThrow(player, slotId, action);
        try {
            original.call(gameMode, containerId, slotId, button, action, player);
        } finally {
            SkyBlockMenuInventoryDropFix.finishContainerThrow(guard);
        }
    }

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void skysoft$keepStorageOverlayClicksInside(
        double mouseX,
        double mouseY,
        int left,
        int top,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (ItemListController.isClickInside((AbstractContainerScreen<?>) (Object) this, mouseX, mouseY)) {
            cir.setReturnValue(false);
            return;
        }
        if (StorageOverlayController.isClickInsideOverlay((AbstractContainerScreen<?>) (Object) this, mouseX, mouseY)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void skysoft$renderActivePetHighlightBackground(
        GuiGraphicsExtractor context,
        Slot slot,
        int mouseX,
        int mouseY,
        CallbackInfo ci
    ) {
        ActivePetHighlighter.renderBackground((AbstractContainerScreen<?>) (Object) this, context, slot);
        BazaarTracker.renderSlotIndicatorBackground((AbstractContainerScreen<?>) (Object) this, context, slot);
    }

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void skysoft$renderActivePetHighlightOutline(
        GuiGraphicsExtractor context,
        Slot slot,
        int mouseX,
        int mouseY,
        CallbackInfo ci
    ) {
        ActivePetHighlighter.renderOutline((AbstractContainerScreen<?>) (Object) this, context, slot);
        BazaarTracker.renderSlotIndicatorOverlay((AbstractContainerScreen<?>) (Object) this, context, slot);
        SlotLockManager.renderSlotOverlay(context, slot);
    }

    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void skysoft$rememberSmoothSwappingSlot(
        GuiGraphicsExtractor context,
        Slot slot,
        int mouseX,
        int mouseY,
        CallbackInfo ci
    ) {
        skysoft$smoothSwappingSlot = slot;
    }

    @Inject(method = "extractSlot", at = @At("RETURN"))
    private void skysoft$clearSmoothSwappingSlot(
        GuiGraphicsExtractor context,
        Slot slot,
        int mouseX,
        int mouseY,
        CallbackInfo ci
    ) {
        skysoft$smoothSwappingSlot = null;
    }

    @Redirect(
        method = "extractSlot",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"
        )
    )
    private void skysoft$suppressSmoothSwappingItem(
        GuiGraphicsExtractor context,
        ItemStack stack,
        int x,
        int y,
        int seed
    ) {
        if (InventoryEquipment.isEquipmentSlot(skysoft$smoothSwappingSlot)
            || !SmoothSwapping.shouldSuppressSlot((AbstractContainerScreen<?>) (Object) this, skysoft$smoothSwappingSlot)
        ) {
            ItemStack renderStack = PlayerHeadSkinFix.inventoryStack(skysoft$smoothSwappingSlot, stack);
            if (renderStack != null) {
                context.item(renderStack, x, y, seed);
            }
        }
    }

    @Redirect(
        method = "extractSlot",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fakeItem(Lnet/minecraft/world/item/ItemStack;III)V"
        )
    )
    private void skysoft$suppressSmoothSwappingFakeItem(
        GuiGraphicsExtractor context,
        ItemStack stack,
        int x,
        int y,
        int seed
    ) {
        if (InventoryEquipment.isEquipmentSlot(skysoft$smoothSwappingSlot)
            || !SmoothSwapping.shouldSuppressSlot((AbstractContainerScreen<?>) (Object) this, skysoft$smoothSwappingSlot)
        ) {
            ItemStack renderStack = PlayerHeadSkinFix.inventoryStack(skysoft$smoothSwappingSlot, stack);
            if (renderStack != null) {
                context.fakeItem(renderStack, x, y, seed);
            }
        }
    }

    @Redirect(
        method = "extractSlot",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"
        )
    )
    private void skysoft$suppressSmoothSwappingItemDecorations(
        GuiGraphicsExtractor context,
        Font font,
        ItemStack stack,
        int x,
        int y,
        String text
    ) {
        if (
            (InventoryEquipment.isEquipmentSlot(skysoft$smoothSwappingSlot)
                || !SmoothSwapping.shouldSuppressSlot((AbstractContainerScreen<?>) (Object) this, skysoft$smoothSwappingSlot))
                && PlayerHeadSkinFix.inventoryStack(skysoft$smoothSwappingSlot, stack) != null
        ) {
            context.itemDecorations(font, stack, x, y, text);
        }
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void skysoft$slotClicked(Slot slot, int slotId, int button, ContainerInput action, CallbackInfo ci) {
        if (SlotLockManager.handleSlotClick((AbstractContainerScreen<?>) (Object) this, slot, button, action)
            == InputHandlingResult.CONSUMED
        ) {
            ci.cancel();
            return;
        }
        PetStorageService.onSlotClick(slot, slotId, button);
        if (SlotBindingManager.handleSlotClick((AbstractContainerScreen<?>) (Object) this, slot, action)
            == InputHandlingResult.CONSUMED
        ) {
            ci.cancel();
        }
    }
}
