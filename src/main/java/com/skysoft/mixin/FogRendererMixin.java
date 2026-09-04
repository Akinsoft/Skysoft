package com.skysoft.mixin;

import com.skysoft.config.SkyColorConfig;
import com.skysoft.config.SkysoftConfigGui;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.material.FogType;
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
        SkyColorConfig config = SkysoftConfigGui.INSTANCE.config().misc.skyColor;
        if (!config.enabled || camera.getFluidInCamera() != FogType.NONE) {
            return;
        }

        int rgb = config.details.horizonColor.get().getEffectiveColourRGB();
        destination.set(ARGB.vector4fFromARGB32(rgb | 0xFF000000));
    }
}
