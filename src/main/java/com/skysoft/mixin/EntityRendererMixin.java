package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.features.combat.BetterShurikens;
import com.skysoft.features.pets.VisiblePetPosition;
import com.skysoft.utils.render.EntityHighlightRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    @Inject(method = "finalizeRenderState", at = @At("TAIL"))
    protected void skysoftAdjustVisiblePetPosition(Entity entity, EntityRenderState state, CallbackInfo ci) {
        VisiblePetPosition.adjustRenderState(entity, state);
        BetterShurikens.adjustNameTag(entity, state);
    }

    @ModifyReturnValue(method = "getBoundingBoxForCulling", at = @At("RETURN"))
    protected AABB skysoftInflateVisiblePetCulling(AABB original, Entity entity) {
        return VisiblePetPosition.shouldInflateCulling(entity)
            ? original.inflate(2.0D, 5.0D, 2.0D)
            : original;
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z")
    )
    protected boolean shouldSkysoftEntityAppearGlowing(Minecraft minecraft, Entity entity, Operation<Boolean> original) {
        return original.call(minecraft, entity) || skysoftGetGlowColor(entity) != null;
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I")
    )
    protected int skysoftGetTeamColor(Entity entity, Operation<Integer> original) {
        int originalColor = original.call(entity);
        Integer color = skysoftGetGlowColor(entity);
        return color != null ? color : originalColor;
    }

    private Integer skysoftGetGlowColor(Entity entity) {
        return EntityHighlightRenderer.getEntityGlowColor(entity);
    }
}
