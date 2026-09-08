package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.skysoft.utils.render.WorldItemBadgeRenderer;
import com.skysoft.utils.render.WorldItemRenderLayers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemFeatureRenderer.class)
public class ItemFeatureRendererMixin {
    @WrapOperation(
        method = "renderItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/resources/model/geometry/BakedQuad$MaterialInfo;itemRenderType()Lnet/minecraft/client/renderer/rendertype/RenderType;"
        )
    )
    private RenderType skysoftUseThroughWallsRenderType(
        BakedQuad.MaterialInfo materialInfo,
        Operation<RenderType> original,
        @Local(argsOnly = true) SubmitNodeStorage.ItemSubmit itemSubmit
    ) {
        RenderType renderType = original.call(materialInfo);
        return MixinErrorBoundary.value("World Item render type", renderType, () ->
            itemSubmit.outlineColor() == WorldItemBadgeRenderer.THROUGH_WALLS_MARKER
                ? WorldItemRenderLayers.throughWalls(materialInfo.sprite().atlasLocation(), renderType.hasBlending())
                : renderType
        );
    }

    @WrapOperation(
        method = "renderItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeStorage$ItemSubmit;outlineColor()I"
        )
    )
    private int skysoftHideRenderModeMarker(
        SubmitNodeStorage.ItemSubmit itemSubmit,
        Operation<Integer> original
    ) {
        int outlineColor = original.call(itemSubmit);
        return MixinErrorBoundary.value("World Item render marker", outlineColor, () ->
            outlineColor == WorldItemBadgeRenderer.THROUGH_WALLS_MARKER ? 0 : outlineColor
        );
    }
}
