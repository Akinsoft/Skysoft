package com.skysoft.mixin

import com.skysoft.features.event.diana.DianaRareMobEntityMatcher
import com.skysoft.features.misc.DeadEntityHider
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRenderDispatcher
import net.minecraft.world.entity.Entity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(EntityRenderDispatcher::class)
abstract class EntityRenderDispatcherMixin {
    @Inject(method = ["shouldRender"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftHideEntity(
        entity: Entity,
        frustum: Frustum,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        cir: CallbackInfoReturnable<Boolean>,
    ) {
        val shouldHideDead = SkysoftErrorBoundary.value("Dead Entity hiding", false) {
            DeadEntityHider.shouldHide(entity)
        }
        val shouldHideDiana = SkysoftErrorBoundary.value("Diana bugged entity hiding", false) {
            DianaRareMobEntityMatcher.shouldHideBuggedEntity(entity)
        }
        if (shouldHideDead || shouldHideDiana) {
            cir.returnValue = false
        }
    }
}
