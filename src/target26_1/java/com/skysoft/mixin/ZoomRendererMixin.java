package com.skysoft.mixin;

import com.skysoft.features.misc.Zoom;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class ZoomRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void skysoftApplyZoom(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(Zoom.applyFov(cir.getReturnValue()));
    }
}
