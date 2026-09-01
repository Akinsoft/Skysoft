package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.skysoft.features.event.diana.DianaRareMobEntityMatcher;
import com.skysoft.features.foraging.ThrowingAxeGhostFix;
import com.skysoft.features.misc.DeadEntityHider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
    protected boolean skysoftHideEntity(
        boolean original,
        Entity entity,
        Frustum frustum,
        double cameraX,
        double cameraY,
        double cameraZ
    ) {
        return original
            && !DeadEntityHider.shouldHide(entity)
            && !DianaRareMobEntityMatcher.shouldHideBuggedEntity(entity)
            && !ThrowingAxeGhostFix.shouldHide(entity);
    }
}
