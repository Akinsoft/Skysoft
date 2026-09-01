package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.skysoft.features.inventory.StorageOverlayController;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "me.shedaniel.rei.RoughlyEnoughItemsCoreClient", remap = false)
public abstract class ReiCoreClientMixin {
    @ModifyReturnValue(
        method = "shouldReturn(Lnet/minecraft/client/gui/screens/Screen;)Z",
        at = @At("RETURN"),
        remap = false
    )
    private static boolean skysoftShouldReturnForStorage(boolean original, Screen screen) {
        return original
            || screen instanceof AbstractContainerScreen<?> containerScreen
            && StorageOverlayController.isActive(containerScreen);
    }
}
