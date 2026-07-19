package com.skysoft.mixin

import com.mojang.blaze3d.vertex.PoseStack
import com.skysoft.features.misc.DroppedItemScaleRenderState
import com.skysoft.features.misc.DroppedItemScaling
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.ItemEntityRenderer
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.world.entity.item.ItemEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

private const val DEFAULT_DROPPED_ITEM_SCALE = 1f

@Mixin(ItemEntityRenderer::class)
abstract class ItemEntityRendererMixin {
    @Inject(method = ["extractRenderState"], at = [At("TAIL")])
    protected fun skysoftExtractDroppedItemScale(
        entity: ItemEntity,
        state: ItemEntityRenderState,
        partialTick: Float,
        ci: CallbackInfo,
    ) {
        val scaleState = state as DroppedItemScaleRenderState
        scaleState.skysoftSetDroppedItemScale(DEFAULT_DROPPED_ITEM_SCALE)
        if (!DroppedItemScaling.isActive()) return
        val scale = SkysoftErrorBoundary.value(
            "Dropped Item Scaling extraction",
            DEFAULT_DROPPED_ITEM_SCALE,
        ) {
            DroppedItemScaling.scaleFor(entity.item)
        }
        scaleState.skysoftSetDroppedItemScale(scale)
    }

    @Inject(
        method = ["submit"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;" +
                    "submitMultipleFromCount(Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/client/renderer/SubmitNodeCollector;I" +
                    "Lnet/minecraft/client/renderer/entity/state/ItemClusterRenderState;" +
                    "Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/phys/AABB;)V",
            ),
        ],
    )
    protected fun skysoftScaleDroppedItem(
        state: ItemEntityRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState,
        ci: CallbackInfo,
    ) {
        val scale = (state as DroppedItemScaleRenderState).skysoftGetDroppedItemScale()
        if (scale == DEFAULT_DROPPED_ITEM_SCALE) return
        SkysoftErrorBoundary.run("Dropped Item Scaling rendering") {
            DroppedItemScaling.applyRenderScale(poseStack, scale, state.item.modelBoundingBox.minY.toFloat())
        }
    }
}
