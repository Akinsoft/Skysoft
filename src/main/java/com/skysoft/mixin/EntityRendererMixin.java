package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.features.combat.BetterShurikens;
import com.skysoft.features.hunting.LassoDisplay;
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
public abstract class EntityRendererMixin {
    @Inject(method = "finalizeRenderState", at = @At("TAIL"))
    private void skysoftAdjustVisiblePetPosition(Entity entity, EntityRenderState state, CallbackInfo ci) {
        VisiblePetPosition.adjustRenderState(entity, state);
        BetterShurikens.adjustNameTag(entity, state);
        LassoDisplay.adjustNameTag(entity, state);
    }

    @ModifyReturnValue(method = "getBoundingBoxForCulling", at = @At("RETURN"))
    private AABB skysoftInflateVisiblePetCulling(AABB original, Entity entity) {
        return VisiblePetPosition.shouldInflateCulling(entity)
            ? original.inflate(2.0D, 5.0D, 2.0D)
            : original;
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z")
    )
    private boolean shouldSkysoftEntityAppearGlowing(Minecraft minecraft, Entity entity, Operation<Boolean> original) {
        return original.call(minecraft, entity) || EntityHighlightRenderer.getEntityGlowColor(entity) != null;
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I")
    )
    private int skysoftGetTeamColor(Entity entity, Operation<Integer> original) {
        int originalColor = original.call(entity);
        Integer color = EntityHighlightRenderer.getEntityGlowColor(entity);
        return color != null ? color : originalColor;
    }
}
