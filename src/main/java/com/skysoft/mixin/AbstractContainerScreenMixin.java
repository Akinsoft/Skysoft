package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.features.bazaar.BazaarTracker;
import com.skysoft.features.inventory.InventoryButtonManager;
import com.skysoft.features.inventory.InventoryEquipment;
import com.skysoft.features.inventory.InventoryDropSelectionGuard;
import com.skysoft.features.inventory.ItemProtectionManager;
import com.skysoft.features.inventory.MinisterCalendarTooltip;
import com.skysoft.features.inventory.SkyBlockMenuInventoryDropFix;
import com.skysoft.features.inventory.SlotBindingManager;
import com.skysoft.features.inventory.SlotLockManager;
import com.skysoft.features.inventory.StorageOverlayController;
import com.skysoft.features.inventory.itemlist.ItemListController;
import com.skysoft.features.pets.PetStorageService;
import com.skysoft.gui.scale.InventoryCursorMemory;
import com.skysoft.gui.tooltip.AdjacentTooltipRenderer;
import com.skysoft.utils.input.InputHandlingResult;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
    @Inject(method = "init()V", at = @At("TAIL"))
    private void skysoftLayoutStorageOverlay(CallbackInfo ci) {
        StorageOverlayController.layoutScreen((AbstractContainerScreen<?>) (Object) this);
        InventoryEquipment.layoutScreen((AbstractContainerScreen<?>) (Object) this);
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void skysoftCleanUpContainerScreen(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        MixinErrorBoundary.run("Inventory cursor screen removal", InventoryCursorMemory::prepareForMouseGrab);
        MixinErrorBoundary.run("Bazaar Tracker screen cleanup", () -> BazaarTracker.restoreOrderMenu(screen));
        MixinErrorBoundary.run("Inventory Equipment screen cleanup", () -> InventoryEquipment.restoreScreen(screen));
        MixinErrorBoundary.run("Slot Lock screen cleanup", SlotLockManager::clearInputState);
        MixinErrorBoundary.run("Item Protection screen cleanup", ItemProtectionManager::clearInputState);
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void skysoftSuppressTooltipDuringSlotBinding(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        AdjacentTooltipRenderer.INSTANCE.clear();
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        boolean suppress = MixinErrorBoundary.value("Slot Binding tooltip suppression", false, () -> SlotBindingManager.shouldSuppressRegularTooltips(screen));
        if (suppress) {
            ci.cancel();
            return;
        }
        MixinErrorBoundary.run("Minister in Calendar tooltip preparation", () -> MinisterCalendarTooltip.INSTANCE.prepare(screen, context));
    }

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void skysoftSuppressStorageOverlayLabels(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        if (StorageOverlayController.shouldSuppressContainerLabels((AbstractContainerScreen<?>) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void skysoftClickStorageOverlay(
        MouseButtonEvent click,
        boolean doubled,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (ItemListController.handleMouseClick((AbstractContainerScreen<?>) (Object) this, click, doubled)
            == InputHandlingResult.CONSUMED
        ) {
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
    private void skysoftReleaseOverlayInput(
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
    private void skysoftDragStorageOverlay(
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
    private boolean isSkysoftStorageOverlayWidgetClickHandled(
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
    private boolean isSkysoftStorageOverlayWidgetDragHandled(
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
    private boolean canSkysoftAddSlotToQuickCraft(
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
    private boolean isSkysoftStorageOverlayWidgetReleaseHandled(
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
    private void skysoftScrollStorageOverlay(
        double mouseX,
        double mouseY,
        double horizontalAmount,
        double verticalAmount,
        CallbackInfoReturnable<Boolean> cir
    ) {
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
    protected void skysoftKeyStorageOverlay(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        InputHandlingResult storage = MixinErrorBoundary.value("Storage Overlay key input", InputHandlingResult.IGNORED, () -> StorageOverlayController.handleKeyPress(screen, event));
        if (storage == InputHandlingResult.CONSUMED) { cir.setReturnValue(true); return; }
        InputHandlingResult itemList = MixinErrorBoundary.value("Item List key input", InputHandlingResult.IGNORED, () -> ItemListController.INSTANCE.handleKeyPress(screen, event));
        if (itemList == InputHandlingResult.CONSUMED) { cir.setReturnValue(true); return; }
        InputHandlingResult slotLock = MixinErrorBoundary.value("Slot Lock key input", InputHandlingResult.IGNORED, () -> SlotLockManager.handleKeyPress(screen, event));
        InputHandlingResult protection = MixinErrorBoundary.value("Item Protection key input", InputHandlingResult.IGNORED, () -> ItemProtectionManager.handleKeyPress(screen, event));
        if (slotLock == InputHandlingResult.CONSUMED || protection == InputHandlingResult.CONSUMED) cir.setReturnValue(true);
    }

    @WrapOperation(
        method = "slotClicked",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V"
        )
    )
    private void skysoftPreventSkyBlockMenuOpeningOnInventoryDrop(
        MultiPlayerGameMode gameMode,
        int containerId,
        int slotId,
        int button,
        ContainerInput action,
        Player player,
        Operation<Void> original
    ) {
        if (BazaarTracker.shouldBlockOrderInteraction((AbstractContainerScreen<?>) (Object) this, slotId)) {
            return;
        }
        InventoryDropSelectionGuard guard = SkyBlockMenuInventoryDropFix.beginContainerThrow(player, slotId, action);
        try {
            original.call(gameMode, containerId, slotId, button, action, player);
        } finally {
            SkyBlockMenuInventoryDropFix.finishContainerThrow(guard);
        }
    }

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void skysoftKeepStorageOverlayClicksInside(
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

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    protected void skysoftSlotClicked(Slot slot, int slotId, int button, ContainerInput action, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        InputHandlingResult protection = MixinErrorBoundary.value("Item Protection slot click", InputHandlingResult.IGNORED, () -> ItemProtectionManager.handleContainerDrop(screen, slot, slotId, action));
        if (protection == InputHandlingResult.CONSUMED) { ci.cancel(); return; }
        InputHandlingResult binding = MixinErrorBoundary.value("Slot Binding slot click", InputHandlingResult.IGNORED, () -> SlotBindingManager.handleSlotClick(screen, slot, action));
        if (binding == InputHandlingResult.CONSUMED) {
            MixinErrorBoundary.run("Pet Storage slot click", () -> PetStorageService.INSTANCE.onSlotClick(slot, slotId, button));
            ci.cancel();
            return;
        }
        InputHandlingResult lock = MixinErrorBoundary.value("Slot Lock slot click", InputHandlingResult.IGNORED, () -> SlotLockManager.handleSlotClick(screen, slot, button, action));
        if (lock == InputHandlingResult.CONSUMED) { ci.cancel(); return; }
        MixinErrorBoundary.run("Pet Storage slot click", () -> PetStorageService.INSTANCE.onSlotClick(slot, slotId, button));
    }
}
