package com.skysoft.mixin

import com.skysoft.features.misc.PlayerHeadSkinFix
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.world.entity.LivingEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(LivingEntityRenderer::class)
open class LivingEntityRendererMixin {
    @Inject(
        method = [
            "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;" +
                "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
        ],
        at = [At("TAIL")],
    )
    protected fun skysoftTrackHeadSkinOwner(
        entity: LivingEntity,
        state: LivingEntityRenderState,
        partialTicks: Float,
        ci: CallbackInfo,
    ) {
        SkysoftErrorBoundary.run("Player Head Skin owner tracking") { PlayerHeadSkinFix.setOwner(state, entity) }
    }
}
