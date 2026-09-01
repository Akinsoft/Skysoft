package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.skysoft.utils.render.EntityHighlightRenderState;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ArmorStandRenderer.class)
public class ArmorStandRendererMixin {
    @ModifyReturnValue(
        method = "getRenderType(Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;",
        at = @At("RETURN")
    )
    private RenderType skysoftRenderHighlightedEquipmentOnly(
        RenderType original,
        ArmorStandRenderState state,
        boolean bodyVisible,
        boolean translucent,
        boolean glowing
    ) {
        return ((EntityHighlightRenderState) state).skysoftHasEquipmentOnlyOutline() ? null : original;
    }
}
