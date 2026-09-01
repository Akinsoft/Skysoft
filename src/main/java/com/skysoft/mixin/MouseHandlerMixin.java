package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import com.skysoft.integration.MouseInputHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Shadow private double xpos;
    @Shadow private double ypos;

    @Inject(method = "grabMouse", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MouseHandler;mouseGrabbed:Z", opcode = Opcodes.PUTFIELD))
    protected void skysoftRememberInventoryCursorGrab(CallbackInfo ci) {
        MouseInputHooks.beginMouseGrab(Minecraft.getInstance().getWindow());
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
    protected void skysoftRestoreInventoryCursorAfterInput(CallbackInfo ci) {
        var cursor = MouseInputHooks.cursorAfterInput();
        if (cursor == null) return;
        xpos = cursor.x();
        ypos = cursor.y();
        GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().handle(), cursor.x(), cursor.y());
    }

    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    protected void skysoftApplyMouseMovement(LocalPlayer player, double x, double y, Operation<Void> original) {
        original.call(player, MouseInputHooks.applyMovement(x), MouseInputHooks.applyMovement(y));
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    protected void skysoftProcessGlobalScroll(long window, double horizontalAmount, double verticalAmount, CallbackInfo ci) {
        if (MouseInputHooks.shouldConsumeScroll(verticalAmount)) ci.cancel();
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    protected void skysoftProcessGlobalMouseButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        if (MouseInputHooks.shouldConsumeButton(buttonInfo.button(), action)) ci.cancel();
    }

    @WrapOperation(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseScrolled(DDDD)Z"))
    protected boolean doesSkysoftHandleTooltipScroll(
        Screen screen,
        double mouseX,
        double mouseY,
        double horizontalAmount,
        double verticalAmount,
        Operation<Boolean> original
    ) {
        return MouseInputHooks.didHandleTooltipScroll(screen, mouseX, mouseY, horizontalAmount, verticalAmount)
            || original.call(screen, mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @ModifyReturnValue(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;D)D", at = @At("RETURN"))
    private static double skysoftGetInventoryScaledX(double original, Window window, double xPosition) {
        Double scaled = MouseInputHooks.inventoryScaledX(window, xPosition);
        return scaled != null ? scaled : original;
    }

    @ModifyReturnValue(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;D)D", at = @At("RETURN"))
    private static double skysoftGetInventoryScaledY(double original, Window window, double yPosition) {
        Double scaled = MouseInputHooks.inventoryScaledY(window, yPosition);
        return scaled != null ? scaled : original;
    }
}
