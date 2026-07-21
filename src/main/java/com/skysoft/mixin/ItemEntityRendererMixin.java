package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.mojang.blaze3d.vertex.PoseStack;
import com.skysoft.features.misc.DroppedItemScaleRenderState;
import com.skysoft.features.misc.DroppedItemScaling;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    private static final float DEFAULT_DROPPED_ITEM_SCALE = 1.0F;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    protected void skysoftExtractDroppedItemScale(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
        DroppedItemScaleRenderState scaleState = (DroppedItemScaleRenderState) state;
        scaleState.skysoftSetDroppedItemScale(DEFAULT_DROPPED_ITEM_SCALE);
        if (!DroppedItemScaling.INSTANCE.isActive()) return;
        float scale = MixinErrorBoundary.value("Dropped Item Scaling extraction", DEFAULT_DROPPED_ITEM_SCALE,
            () -> DroppedItemScaling.INSTANCE.scaleFor(entity.getItem()));
        scaleState.skysoftSetDroppedItemScale(scale);
    }

    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;submitMultipleFromCount(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ItemClusterRenderState;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/phys/AABB;)V"))
    protected void skysoftScaleDroppedItem(ItemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        float scale = ((DroppedItemScaleRenderState) state).skysoftGetDroppedItemScale();
        if (scale == DEFAULT_DROPPED_ITEM_SCALE) return;
        MixinErrorBoundary.run("Dropped Item Scaling rendering", () -> DroppedItemScaling.INSTANCE.applyRenderScale(poseStack, scale, (float) state.item.getModelBoundingBox().minY));
    }
}
