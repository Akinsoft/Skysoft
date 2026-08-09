package com.skysoft.mixin;

import com.skysoft.features.misc.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class ZoomRendererMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void skysoftApplyZoom(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue((float) Zoom.applyFov(cir.getReturnValue()));
    }
}
