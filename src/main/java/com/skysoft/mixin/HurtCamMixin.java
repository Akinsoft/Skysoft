package com.skysoft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skysoft.config.SkysoftConfig;
import com.skysoft.config.SkysoftConfigGui;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class HurtCamMixin {

    @Unique
    SkysoftConfig config = SkysoftConfigGui.INSTANCE.config();

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    public void onCamHurt(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if (config.misc.noHurtCam)
            ci.cancel();
    }
}
