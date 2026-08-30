package com.skysoft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skysoft.features.misc.PlayerHeadSkinFix;
import com.skysoft.utils.render.EntityHighlightRenderer;
import com.skysoft.utils.render.EntityHighlightRenderLayers;
import com.skysoft.utils.render.EntityHighlightRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
    @Shadow protected M model;
    @Shadow public abstract Identifier getTextureLocation(S state);

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void skysoftPrepareRenderState(LivingEntity entity, S state, float partialTicks, CallbackInfo ci) {
        PlayerHeadSkinFix.setOwner(state, entity);
        Integer fillColor = EntityHighlightRenderer.getEntityFillColor(entity);
        EntityHighlightRenderState highlightState = (EntityHighlightRenderState) state;
        highlightState.skysoftSetEntityFillColor(fillColor != null ? fillColor : 0);
        highlightState.skysoftSetEquipmentOnlyOutline(
            entity instanceof ArmorStand && EntityHighlightRenderer.getEntityGlowColor(entity) != null
        );
    }

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            shift = At.Shift.AFTER
        )
    )
    private void skysoftSubmitHighlightFill(
        S state,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        CameraRenderState camera,
        CallbackInfo ci
    ) {
        int fillColor = ((EntityHighlightRenderState) state).skysoftGetEntityFillColor();
        if (fillColor == 0) return;
        submitNodeCollector.submitModel(
            model,
            state,
            poseStack,
            EntityHighlightRenderLayers.fill(getTextureLocation(state)),
            state.lightCoords,
            0,
            fillColor,
            null,
            0,
            null
        );
    }
}
