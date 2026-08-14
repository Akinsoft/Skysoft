package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.skysoft.features.misc.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public class ZoomRendererMixin {
    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float skysoftApplyZoom(float fov) {
        return (float) Zoom.applyFov(fov);
    }
}
