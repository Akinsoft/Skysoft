package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.sugar.Local
import com.mojang.blaze3d.vertex.PoseStack
import com.skysoft.features.misc.PlayerHeadSkinFix
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.model.`object`.skull.SkullModelBase
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderType
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(CustomHeadLayer::class)
abstract class CustomHeadLayerMixin {
    @WrapOperation(
        method = ["submit"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/blockentity/SkullBlockRenderer;" +
                    "submitSkull(FLcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/client/renderer/SubmitNodeCollector;I" +
                    "Lnet/minecraft/client/model/object/skull/SkullModelBase;" +
                    "Lnet/minecraft/client/renderer/rendertype/RenderType;I" +
                    "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer\$CrumblingOverlay;)V",
            ),
        ],
    )
    protected fun skysoftSkipLoadingPlayerHeadSkin(
        animationValue: Float,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        lightCoords: Int,
        model: SkullModelBase,
        renderType: RenderType,
        outlineColor: Int,
        breakProgress: ModelFeatureRenderer.CrumblingOverlay?,
        original: Operation<Void>,
        @Local(argsOnly = true) state: LivingEntityRenderState,
    ) {
        val fixedRenderType = SkysoftErrorBoundary.value("Player Head Skin render type", renderType) {
            PlayerHeadSkinFix.wornHeadRenderType(state, renderType)
        }
        if (fixedRenderType != null) {
            original.call(
                animationValue,
                poseStack,
                submitNodeCollector,
                lightCoords,
                model,
                fixedRenderType,
                outlineColor,
                breakProgress,
            )
        }
    }
}
