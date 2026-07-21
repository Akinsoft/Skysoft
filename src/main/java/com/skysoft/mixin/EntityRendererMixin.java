package com.skysoft.mixin;

import com.skysoft.features.combat.BetterShurikens;
import com.skysoft.features.pets.VisiblePetPosition;
import com.skysoft.utils.render.EntityHighlightRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    @Inject(method = "finalizeRenderState", at = @At("TAIL"))
    protected void skysoftAdjustVisiblePetPosition(Entity entity, EntityRenderState state, CallbackInfo ci) {
        VisiblePetPosition.adjustRenderState(entity, state);
        BetterShurikens.adjustNameTag(entity, state);
    }

    @Inject(method = "getBoundingBoxForCulling", at = @At("RETURN"), cancellable = true)
    protected void skysoftInflateVisiblePetCulling(Entity entity, CallbackInfoReturnable<AABB> cir) {
        if (VisiblePetPosition.shouldInflateCulling(entity)) {
            cir.setReturnValue(cir.getReturnValue().inflate(2.0D, 5.0D, 2.0D));
        }
    }

    @Redirect(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z")
    )
    protected boolean shouldSkysoftEntityAppearGlowing(Minecraft minecraft, Entity entity) {
        return skysoftGetGlowColor(entity) != null || minecraft.shouldEntityAppearGlowing(entity);
    }

    @Redirect(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I")
    )
    protected int skysoftGetTeamColor(Entity entity) {
        Integer color = skysoftGetGlowColor(entity);
        return color != null ? color : entity.getTeamColor();
    }

    private Integer skysoftGetGlowColor(Entity entity) {
        if (!(entity instanceof LivingEntity livingEntity)) {
            return null;
        }
        return EntityHighlightRenderer.getEntityGlowColor(livingEntity);
    }
}
