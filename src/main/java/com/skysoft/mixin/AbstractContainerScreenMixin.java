package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.integration.ContainerInputHooks;
import com.skysoft.integration.ContainerLifecycleHooks;
import com.skysoft.integration.ContainerSlotInputHooks;
import com.skysoft.integration.ContainerTooltipHooks;
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
    private void skysoftLayoutContainerScreen(CallbackInfo ci) {
        ContainerLifecycleHooks.layout((AbstractContainerScreen<?>) (Object) this);
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void skysoftCleanUpContainerScreen(CallbackInfo ci) {
        ContainerLifecycleHooks.removed((AbstractContainerScreen<?>) (Object) this);
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void skysoftPrepareTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (ContainerTooltipHooks.shouldSuppressTooltip(screen)) {
            ci.cancel();
        } else {
            ContainerTooltipHooks.prepareTooltip(screen, context);
        }
    }

    @WrapOperation(method = "extractTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack skysoftTransformTooltipStack(Slot slot, Operation<ItemStack> original) {
        ItemStack stack = original.call(slot);
        return ContainerTooltipHooks.tooltipStack((AbstractContainerScreen<?>) (Object) this, slot, stack);
    }

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void skysoftSuppressContainerLabels(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        if (ContainerTooltipHooks.shouldSuppressLabels((AbstractContainerScreen<?>) (Object) this)) ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void skysoftClickContainerOverlay(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (ContainerInputHooks.didConsumeMouseClick((AbstractContainerScreen<?>) (Object) this, click, doubled)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void skysoftReleaseContainerOverlay(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        if (ContainerInputHooks.didConsumeMouseRelease((AbstractContainerScreen<?>) (Object) this, click)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void skysoftDragContainerOverlay(
        MouseButtonEvent click,
        double deltaX,
        double deltaY,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (ContainerInputHooks.didConsumeMouseDrag((AbstractContainerScreen<?>) (Object) this, click)) {
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
    private boolean isSkysoftContainerWidgetClickHandled(
        AbstractContainerScreen<?> screen,
        MouseButtonEvent click,
        boolean doubled,
        Operation<Boolean> original
    ) {
        return !ContainerInputHooks.shouldSuppressScreenWidgets(screen) && original.call(screen, click, doubled);
    }

    @WrapOperation(
        method = "mouseDragged",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z"
        )
    )
    private boolean isSkysoftContainerWidgetDragHandled(
        AbstractContainerScreen<?> screen,
        MouseButtonEvent click,
        double deltaX,
        double deltaY,
        Operation<Boolean> original
    ) {
        return !ContainerInputHooks.shouldSuppressScreenWidgets(screen) && original.call(screen, click, deltaX, deltaY);
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
        return ContainerSlotInputHooks.canQuickCraftInto(slot) && original.call(screen, slot, stack);
    }

    @WrapOperation(
        method = "mouseReleased",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z"
        )
    )
    private boolean isSkysoftContainerWidgetReleaseHandled(
        AbstractContainerScreen<?> screen,
        MouseButtonEvent click,
        Operation<Boolean> original
    ) {
        return !ContainerInputHooks.shouldSuppressScreenWidgets(screen) && original.call(screen, click);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void skysoftScrollContainerOverlay(
        double mouseX,
        double mouseY,
        double horizontalAmount,
        double verticalAmount,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (ContainerInputHooks.didConsumeMouseScroll((AbstractContainerScreen<?>) (Object) this, mouseX, mouseY, verticalAmount)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    protected void skysoftKeyContainerOverlay(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (ContainerInputHooks.didConsumeKeyPress((AbstractContainerScreen<?>) (Object) this, event)) {
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
    private void skysoftHandleContainerInput(
        MultiPlayerGameMode gameMode,
        int containerId,
        int slotId,
        int button,
        ContainerInput action,
        Player player,
        Operation<Void> original
    ) {
        ContainerSlotInputHooks.handleContainerInput(
            (AbstractContainerScreen<?>) (Object) this,
            slotId,
            action,
            player,
            () -> original.call(gameMode, containerId, slotId, button, action, player)
        );
    }

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void skysoftKeepOverlayClicksInside(
        double mouseX,
        double mouseY,
        int left,
        int top,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (ContainerInputHooks.isPointCovered((AbstractContainerScreen<?>) (Object) this, mouseX, mouseY)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    protected void skysoftSlotClicked(Slot slot, int slotId, int button, ContainerInput action, CallbackInfo ci) {
        if (ContainerSlotInputHooks.didConsumeSlotClick((AbstractContainerScreen<?>) (Object) this, slot, slotId, button, action)) {
            ci.cancel();
        }
    }
}
