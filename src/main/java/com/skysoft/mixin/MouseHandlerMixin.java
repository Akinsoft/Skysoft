package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import com.skysoft.features.bazaar.BazaarTracker;
import com.skysoft.features.inventory.StorageOverlayController;
import com.skysoft.features.screenshot.ScreenshotCapturePreview;
import com.skysoft.gui.scale.CursorController;
import com.skysoft.gui.scale.GuiScaleController;
import com.skysoft.gui.scale.InventoryCursorMemory;
import com.skysoft.gui.tooltip.TooltipViewport;
import com.skysoft.utils.MinecraftClient;
import com.skysoft.utils.input.InputHandlingResult;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonInfo;
import org.objectweb.asm.Opcodes;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin implements CursorController {
    @Shadow private double xpos;
    @Shadow private double ypos;

    @Override public void skysoftMoveCursor(double cursorX, double cursorY) { xpos = cursorX; ypos = cursorY; }

    @Inject(method = "grabMouse", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MouseHandler;mouseGrabbed:Z", opcode = Opcodes.PUTFIELD))
    protected void skysoftSaveCursorBeforeGrab(CallbackInfo ci) { MixinErrorBoundary.run("Inventory cursor mouse grab", () -> InventoryCursorMemory.beginMouseGrab(xpos, ypos)); }

    @Inject(method = "grabMouse", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(Lcom/mojang/blaze3d/platform/Window;IDD)V"))
    protected void skysoftSaveCursorAfterGrab(CallbackInfo ci) { MixinErrorBoundary.run("Inventory cursor mouse grab", () -> InventoryCursorMemory.finishMouseGrab(xpos, ypos)); }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    protected void skysoftProcessOverlayMouseControl(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        boolean consumed = MixinErrorBoundary.value("Overlay mouse control", false, () -> action == GLFW.GLFW_PRESS &&
            (ScreenshotCapturePreview.INSTANCE.processMouseButtonPress(buttonInfo.button()) == InputHandlingResult.CONSUMED || BazaarTracker.handleMouseButtonPress(buttonInfo.button()) == InputHandlingResult.CONSUMED));
        if (consumed) ci.cancel();
    }

    @WrapOperation(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseScrolled(DDDD)Z"))
    protected boolean doesSkysoftHandleTooltipScroll(Screen screen, double mouseX, double mouseY, double horizontalAmount, double verticalAmount, Operation<Boolean> original) {
        boolean handled = MixinErrorBoundary.value("Tooltip mouse scrolling", false, () -> {
            boolean overStorage = screen instanceof AbstractContainerScreen<?> container && StorageOverlayController.shouldPreferMouseScroll(container, mouseX, mouseY, verticalAmount);
            return overStorage ? TooltipViewport.isStorageOverlayScrollKeyDown() && TooltipViewport.didHandleStorageMouseScroll(horizontalAmount, verticalAmount) : TooltipViewport.didHandleMouseScroll(horizontalAmount, verticalAmount);
        });
        return handled || original.call(screen, mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @WrapOperation(method = "releaseMouse", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(Lcom/mojang/blaze3d/platform/Window;IDD)V"))
    protected void skysoftRestoreCursorOnRelease(Window window, int cursorMode, double initialCursorX, double initialCursorY, Operation<Void> original) {
        InventoryCursorMemory.CursorPoint restored = MixinErrorBoundary.value("Inventory cursor release", null, () -> InventoryCursorMemory.cursorForRelease(initialCursorX, initialCursorY));
        double cursorX = restored == null ? initialCursorX : restored.x();
        double cursorY = restored == null ? initialCursorY : restored.y();
        if (restored != null) { xpos = cursorX; ypos = cursorY; }
        original.call(window, cursorMode, cursorX, cursorY);
    }

    @Inject(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;D)D", at = @At("HEAD"), cancellable = true)
    private static void skysoftGetInventoryScaledX(Window window, double xPosition, CallbackInfoReturnable<Double> cir) {
        Double scaled = MixinErrorBoundary.value("Inventory GUI scaled mouse X", null, () -> {
            Screen screen = MinecraftClient.INSTANCE.screen();
            if (!GuiScaleController.usesSeparateInventoryScale(screen) || GuiScaleController.areOverlaysUsingNormalCoordinates()) return null;
            try (var ignored = GuiScaleController.useInventoryScale(screen, window)) { return xPosition * window.getGuiScaledWidth() / (double) window.getScreenWidth(); }
        });
        if (scaled != null) cir.setReturnValue(scaled);
    }

    @Inject(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;D)D", at = @At("HEAD"), cancellable = true)
    private static void skysoftGetInventoryScaledY(Window window, double yPosition, CallbackInfoReturnable<Double> cir) {
        Double scaled = MixinErrorBoundary.value("Inventory GUI scaled mouse Y", null, () -> {
            Screen screen = MinecraftClient.INSTANCE.screen();
            if (!GuiScaleController.usesSeparateInventoryScale(screen) || GuiScaleController.areOverlaysUsingNormalCoordinates()) return null;
            try (var ignored = GuiScaleController.useInventoryScale(screen, window)) { return yPosition * window.getGuiScaledHeight() / (double) window.getScreenHeight(); }
        });
        if (scaled != null) cir.setReturnValue(scaled);
    }
}
