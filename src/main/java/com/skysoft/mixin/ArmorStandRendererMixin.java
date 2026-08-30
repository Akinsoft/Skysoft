package com.skysoft.mixin;

import com.skysoft.utils.render.EntityHighlightRenderState;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorStandRenderer.class)
public class ArmorStandRendererMixin {
    @Inject(
        method = "getRenderType(Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void skysoftRenderHighlightedEquipmentOnly(
        ArmorStandRenderState state,
        boolean bodyVisible,
        boolean translucent,
        boolean glowing,
        CallbackInfoReturnable<RenderType> cir
    ) {
        if (((EntityHighlightRenderState) state).skysoftHasEquipmentOnlyOutline()) {
            cir.setReturnValue(null);
        }
    }
}
