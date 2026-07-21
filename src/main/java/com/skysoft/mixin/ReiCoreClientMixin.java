package com.skysoft.mixin;

import com.skysoft.features.inventory.StorageOverlayController;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.shedaniel.rei.RoughlyEnoughItemsCoreClient", remap = false)
public abstract class ReiCoreClientMixin {
    @Inject(
        method = "shouldReturn(Lnet/minecraft/client/gui/screens/Screen;)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void skysoftShouldReturnForStorage(Screen screen, CallbackInfoReturnable<Boolean> cir) {
        if (screen instanceof AbstractContainerScreen<?> containerScreen && StorageOverlayController.isActive(containerScreen)) {
            cir.setReturnValue(true);
        }
    }
}
