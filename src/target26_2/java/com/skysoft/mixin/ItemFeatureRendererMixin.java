package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.utils.render.WorldItemBadgeRenderer;
import com.skysoft.utils.render.WorldItemRenderLayers;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.class)
public class ItemFeatureRendererMixin {
    @Inject(method = "prepareMainSubmit", at = @At("HEAD"))
    private void skysoftBeginItemRender(ItemFeatureRenderer.Submit submit, CallbackInfo callbackInfo) {
        MixinErrorBoundary.run("World Item render layer", () ->
            WorldItemRenderLayers.INSTANCE.beginItemRender(
                submit.outlineColor() == WorldItemBadgeRenderer.THROUGH_WALLS_MARKER
            )
        );
    }

    @Inject(method = "prepareMainSubmit", at = @At("RETURN"))
    private void skysoftEndItemRender(ItemFeatureRenderer.Submit submit, CallbackInfo callbackInfo) {
        MixinErrorBoundary.run("World Item render layer", WorldItemRenderLayers.INSTANCE::endItemRender);
    }

    @WrapOperation(
        method = "prepareMainSubmit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/resources/model/geometry/BakedQuad$MaterialInfo;itemRenderType()Lnet/minecraft/client/renderer/rendertype/RenderType;"
        )
    )
    private RenderType skysoftUseThroughWallsRenderType(
        BakedQuad.MaterialInfo materialInfo,
        Operation<RenderType> original
    ) {
        RenderType renderType = original.call(materialInfo);
        return MixinErrorBoundary.value("World Item render type", renderType, () ->
            WorldItemRenderLayers.INSTANCE.isRenderingThroughWalls()
                ? WorldItemRenderLayers.throughWalls(materialInfo.sprite().atlasLocation(), renderType.hasBlending())
                : renderType
        );
    }

    @WrapOperation(
        method = "prepareSubmit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer$Submit;outlineColor()I"
        )
    )
    private int skysoftHideRenderModeMarker(
        ItemFeatureRenderer.Submit submit,
        Operation<Integer> original
    ) {
        int outlineColor = original.call(submit);
        return MixinErrorBoundary.value("World Item render marker", outlineColor, () ->
            outlineColor == WorldItemBadgeRenderer.THROUGH_WALLS_MARKER ? 0 : outlineColor
        );
    }
}
