package com.skysoft.mixin;

import com.skysoft.features.misc.SkyColor;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Inject(method = "computeFogColor", at = @At("TAIL"))
    private void skysoftUseCustomHorizonColor(
        Camera camera,
        float partialTicks,
        ClientLevel level,
        int renderDistance,
        float darkenWorldAmount,
        Vector4f destination,
        CallbackInfo ci
    ) {
        SkyColor.applyHorizonColor(camera, destination);
    }
}
