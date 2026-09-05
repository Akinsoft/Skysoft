package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.skysoft.config.SkysoftConfigGui;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public abstract class CrosshairVisibilityMixin {
    @ModifyExpressionValue(method = "extractCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
    private boolean skysoftCrosshairVisibility(boolean firstPerson) {
        return SkysoftConfigGui.INSTANCE.config().gui.crosshairVisibility.isVisible(firstPerson);
    }
}
