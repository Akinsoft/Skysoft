package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.skysoft.features.misc.PlayerHeadSkinFix;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CustomHeadLayer.class)
public class CustomHeadLayerMixin {
    @WrapOperation(
        method = "submit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/SkullBlockRenderer;submitSkull(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/model/object/skull/SkullModelBase;Lnet/minecraft/client/renderer/rendertype/RenderType;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"
        )
    )
    private void skysoftSkipLoadingPlayerHeadSkin(
        float animationValue,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int lightCoords,
        SkullModelBase model,
        RenderType renderType,
        int outlineColor,
        ModelFeatureRenderer.CrumblingOverlay breakProgress,
        Operation<Void> original,
        @Local(argsOnly = true) LivingEntityRenderState state
    ) {
        RenderType fixedRenderType = PlayerHeadSkinFix.wornHeadRenderType(state, renderType);
        if (fixedRenderType != null) {
            original.call(animationValue, poseStack, submitNodeCollector, lightCoords, model, fixedRenderType, outlineColor, breakProgress);
        }
    }
}
