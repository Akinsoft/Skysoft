package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.features.inventory.StorageOverlayController;
import com.skysoft.gui.GuiOverlayLayer;
import com.skysoft.gui.GuiOverlayRegistry;
import com.skysoft.gui.scale.ScaledScreenState;
import com.skysoft.gui.tooltip.TooltipViewport;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import net.minecraft.client.KeyboardHandler;
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
public abstract class ScreenMixin implements ScaledScreenState {
    @Unique
    private static final int UNSET_SIZE = -1;

    @Unique private int skysoftInventoryGuiWidth = UNSET_SIZE;
    @Unique private int skysoftInventoryGuiHeight = UNSET_SIZE;

    @WrapOperation(method = "defaultHandleClickEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyboardHandler;setClipboard(Ljava/lang/String;)V"))
    private static void skysoftAcknowledgeErrorReportCopy(KeyboardHandler keyboardHandler, String value, Operation<Void> original) {
        original.call(keyboardHandler, value);
        MixinErrorBoundary.acknowledgeClipboardCopy(value);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void skysoftSuppressStorageOverlayWidgets(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (MixinErrorBoundary.value("Storage Overlay widget suppression", false, this::isSkysoftStorageOverlayActive)) ci.cancel();
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void skysoftClearTooltipViewportBeforeRemove(CallbackInfo ci) {
        MixinErrorBoundary.run("Tooltip viewport screen removal", TooltipViewport::clear);
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;extractDeferredElements(IIF)V"))
    private void skysoftRenderScreenForeground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MixinErrorBoundary.run("Storage Overlay background rendering", () -> skysoftRenderStorageOverlay(context, mouseX, mouseY));
        MixinErrorBoundary.run("Screen foreground overlay rendering", () -> GuiOverlayRegistry.INSTANCE.renderLayer(GuiOverlayLayer.BELOW_SCREEN, context));
    }

    @Inject(method = "init(II)V", at = @At("HEAD"))
    private void skysoftClearInventoryGuiSizeBeforeInit(int width, int height, CallbackInfo ci) {
        skysoftForgetScaleDimensions();
    }

    @Inject(method = "resize", at = @At("HEAD"))
    private void skysoftClearInventoryGuiSizeBeforeResize(CallbackInfo ci) {
        skysoftForgetScaleDimensions();
    }

    @Override
    public boolean skysoftHasScaleDimensions() {
        return skysoftInventoryGuiWidth != UNSET_SIZE && skysoftInventoryGuiHeight != UNSET_SIZE;
    }

    @Override
    public boolean skysoftMatchesScaleDimensions(int width, int height) {
        return skysoftInventoryGuiWidth == width && skysoftInventoryGuiHeight == height;
    }

    @Override
    public void skysoftRememberScaleDimensions(int width, int height) {
        skysoftInventoryGuiWidth = width;
        skysoftInventoryGuiHeight = height;
    }

    @Override
    public void skysoftForgetScaleDimensions() {
        skysoftInventoryGuiWidth = UNSET_SIZE;
        skysoftInventoryGuiHeight = UNSET_SIZE;
    }

    @Unique
    private boolean isSkysoftStorageOverlayActive() {
        return (Object) this instanceof AbstractContainerScreen<?> screen && StorageOverlayController.isActive(screen);
    }

    @Unique
    private void skysoftRenderStorageOverlay(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if ((Object) this instanceof ContainerScreen screen && StorageOverlayController.isActive(screen)) {
            context.nextStratum();
            StorageOverlayController.renderBackground(screen, context, mouseX, mouseY);
            screen.extractCarriedItem(context, mouseX, mouseY);
        }
    }
}
