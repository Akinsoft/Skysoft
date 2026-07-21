package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.features.inventory.StorageOverlayController;
import com.skysoft.gui.GuiOverlayLayer;
import com.skysoft.gui.GuiOverlayRegistry;
import com.skysoft.gui.scale.CursorController;
import com.skysoft.gui.scale.InventoryCursorMemory;
import com.skysoft.gui.scale.ScaledScreenState;
import com.skysoft.gui.tooltip.TooltipViewport;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenOverlayCursorMixin implements ScaledScreenState {
    @WrapOperation(method = "defaultHandleClickEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyboardHandler;setClipboard(Ljava/lang/String;)V"))
    private static void skysoftAcknowledgeErrorReportCopy(KeyboardHandler keyboardHandler, String value, Operation<Void> original) {
        original.call(keyboardHandler, value);
        MixinErrorBoundary.acknowledgeClipboardCopy(value);
    }

    @Unique private int skysoftInventoryGuiWidth = -1;
    @Unique private int skysoftInventoryGuiHeight = -1;
    @Override public boolean skysoftHasScaleDimensions() { return skysoftInventoryGuiWidth >= 0 && skysoftInventoryGuiHeight >= 0; }
    @Override public boolean skysoftMatchesScaleDimensions(int width, int height) { return skysoftInventoryGuiWidth == width && skysoftInventoryGuiHeight == height; }
    @Override public void skysoftRememberScaleDimensions(int width, int height) { skysoftInventoryGuiWidth = width; skysoftInventoryGuiHeight = height; }
    @Override public void skysoftForgetScaleDimensions() { skysoftInventoryGuiWidth = -1; skysoftInventoryGuiHeight = -1; }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    protected void skysoftSuppressStorageOverlayWidgets(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (MixinErrorBoundary.value("Storage Overlay widget suppression", false, this::isSkysoftStorageOverlayActive)) ci.cancel();
    }

    @Inject(method = "removed", at = @At("HEAD"))
    protected void skysoftSaveCursorBeforeRemove(CallbackInfo ci) {
        MixinErrorBoundary.run("Inventory cursor screen removal", () -> {
            Minecraft minecraft = Minecraft.getInstance();
            TooltipViewport.clear();
            InventoryCursorMemory.rememberScreenCursor((Screen) (Object) this, minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos());
        });
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", shift = At.Shift.AFTER))
    protected void skysoftRenderBelowScreenOverlays(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MixinErrorBoundary.run("Below-screen overlay rendering", () -> GuiOverlayRegistry.INSTANCE.renderLayer(GuiOverlayLayer.BELOW_SCREEN, context));
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;extractDeferredElements(IIF)V"))
    protected void skysoftRenderStorageOverlayAboveScreenContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MixinErrorBoundary.run("Storage Overlay background rendering", () -> skysoftRenderStorageOverlay(context, mouseX, mouseY));
    }

    @Inject(method = "init(II)V", at = @At("HEAD")) protected void skysoftClearInventoryGuiSizeBeforeInit(int width, int height, CallbackInfo ci) { skysoftForgetScaleDimensions(); }
    @Inject(method = "init(II)V", at = @At("TAIL"))
    protected void skysoftRestoreCursorAfterInit(int width, int height, CallbackInfo ci) {
        MixinErrorBoundary.run("Inventory cursor screen initialization", () -> { Minecraft minecraft = Minecraft.getInstance(); InventoryCursorMemory.restoreWhenScreenInitializes((Screen) (Object) this, minecraft.getWindow(), (CursorController) minecraft.mouseHandler); });
    }
    @Inject(method = "tick", at = @At("HEAD"))
    protected void skysoftRestoreCursorAfterDelayedRecenter(CallbackInfo ci) {
        MixinErrorBoundary.run("Inventory cursor delayed restore", () -> { Minecraft minecraft = Minecraft.getInstance(); InventoryCursorMemory.continueRestore((Screen) (Object) this, minecraft.getWindow(), (CursorController) minecraft.mouseHandler); });
    }
    @Inject(method = "resize", at = @At("HEAD")) protected void skysoftClearInventoryGuiSizeBeforeResize(CallbackInfo ci) { skysoftForgetScaleDimensions(); }

    @Unique private boolean isSkysoftStorageOverlayActive() { return (Object) this instanceof AbstractContainerScreen<?> screen && StorageOverlayController.isActive(screen); }
    @Unique private void skysoftRenderStorageOverlay(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if ((Object) this instanceof ContainerScreen screen && StorageOverlayController.isActive(screen)) {
            context.nextStratum();
            StorageOverlayController.renderBackground(screen, context, mouseX, mouseY);
            screen.extractCarriedItem(context, mouseX, mouseY);
        }
    }
}
