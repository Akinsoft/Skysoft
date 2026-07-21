package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.skysoft.features.inventory.StorageOverlayController;
import com.skysoft.features.inventory.itemlist.ItemListController;
import com.skysoft.utils.MinecraftClient;
import com.skysoft.utils.input.InputHandlingResult;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    protected void skysoftTypeStorageOverlay(long window, CharacterEvent event, CallbackInfo ci) {
        Screen current = MinecraftClient.INSTANCE.screen();
        if (!(current instanceof AbstractContainerScreen<?> screen)) return;
        InputHandlingResult itemList = MixinErrorBoundary.value("Item List character input", InputHandlingResult.IGNORED, () -> ItemListController.INSTANCE.handleCharTyped(screen, event));
        InputHandlingResult storage = MixinErrorBoundary.value("Storage Overlay character input", InputHandlingResult.IGNORED, () -> StorageOverlayController.handleCharTyped(screen, event));
        if (itemList == InputHandlingResult.CONSUMED || storage == InputHandlingResult.CONSUMED) ci.cancel();
    }
}
