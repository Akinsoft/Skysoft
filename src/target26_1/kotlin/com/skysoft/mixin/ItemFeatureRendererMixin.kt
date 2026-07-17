package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.skysoft.utils.render.WorldItemBadgeRenderer
import com.skysoft.utils.render.WorldItemRenderLayers
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.OutlineBufferSource
import net.minecraft.client.renderer.SubmitNodeStorage
import net.minecraft.client.renderer.feature.ItemFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.resources.model.geometry.BakedQuad
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ItemFeatureRenderer::class)
abstract class ItemFeatureRendererMixin {
    @Inject(method = ["renderItem"], at = [At("HEAD")])
    protected fun skysoftBeginItemRender(
        bufferSource: MultiBufferSource.BufferSource,
        outlineBufferSource: OutlineBufferSource,
        itemSubmit: SubmitNodeStorage.ItemSubmit,
        callbackInfo: CallbackInfo,
    ) {
        SkysoftErrorBoundary.run("World Item render layer") {
            WorldItemRenderLayers.beginItemRender(
                itemSubmit.outlineColor() == WorldItemBadgeRenderer.THROUGH_WALLS_MARKER,
            )
        }
    }

    @Inject(method = ["renderItem"], at = [At("RETURN")])
    protected fun skysoftEndItemRender(
        bufferSource: MultiBufferSource.BufferSource,
        outlineBufferSource: OutlineBufferSource,
        itemSubmit: SubmitNodeStorage.ItemSubmit,
        callbackInfo: CallbackInfo,
    ) {
        SkysoftErrorBoundary.run("World Item render layer", WorldItemRenderLayers::endItemRender)
    }

    @WrapOperation(
        method = ["renderItem"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/resources/model/geometry/BakedQuad\$MaterialInfo;" +
                    "itemRenderType()Lnet/minecraft/client/renderer/rendertype/RenderType;",
            ),
        ],
    )
    protected fun skysoftUseThroughWallsRenderType(
        materialInfo: BakedQuad.MaterialInfo,
        original: Operation<RenderType>,
    ): RenderType {
        val renderType = original.call(materialInfo)
        return SkysoftErrorBoundary.value("World Item render type", renderType) {
            if (WorldItemRenderLayers.isRenderingThroughWalls()) {
                WorldItemRenderLayers.throughWalls(materialInfo.sprite().atlasLocation(), renderType.hasBlending())
            } else {
                renderType
            }
        }
    }

    @WrapOperation(
        method = ["renderItem"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/SubmitNodeStorage\$ItemSubmit;outlineColor()I",
            ),
        ],
    )
    protected fun skysoftHideRenderModeMarker(
        itemSubmit: SubmitNodeStorage.ItemSubmit,
        original: Operation<Int>,
    ): Int {
        val outlineColor = original.call(itemSubmit)
        return SkysoftErrorBoundary.value("World Item render marker", outlineColor) {
            if (outlineColor == WorldItemBadgeRenderer.THROUGH_WALLS_MARKER) 0 else outlineColor
        }
    }
}
