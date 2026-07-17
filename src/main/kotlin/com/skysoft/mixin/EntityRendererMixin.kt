package com.skysoft.mixin

import com.skysoft.features.combat.BetterShurikens
import com.skysoft.features.pets.VisiblePetPosition
import com.skysoft.utils.render.EntityHighlightRenderer
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.Redirect
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

private const val VISIBLE_PET_HORIZONTAL_CULLING_INFLATION = 2.0
private const val VISIBLE_PET_VERTICAL_CULLING_INFLATION = 5.0

@Mixin(EntityRenderer::class)
abstract class EntityRendererMixin {
    @Inject(method = ["finalizeRenderState"], at = [At("TAIL")])
    protected fun skysoftAdjustVisiblePetPosition(
        entity: Entity,
        state: EntityRenderState,
        ci: CallbackInfo,
    ) {
        SkysoftErrorBoundary.run("Visible Pet Position render adjustment") {
            VisiblePetPosition.adjustRenderState(entity, state)
        }
        SkysoftErrorBoundary.run("Better Shurikens name tag adjustment") {
            BetterShurikens.adjustNameTag(entity, state)
        }
    }

    @Inject(method = ["getBoundingBoxForCulling"], at = [At("RETURN")], cancellable = true)
    protected fun skysoftInflateVisiblePetCulling(
        entity: Entity,
        cir: CallbackInfoReturnable<AABB>,
    ) {
        val shouldInflate = SkysoftErrorBoundary.value("Visible Pet Position culling", false) {
            VisiblePetPosition.shouldInflateCulling(entity)
        }
        if (shouldInflate) {
            cir.returnValue = cir.returnValue.inflate(
                VISIBLE_PET_HORIZONTAL_CULLING_INFLATION,
                VISIBLE_PET_VERTICAL_CULLING_INFLATION,
                VISIBLE_PET_HORIZONTAL_CULLING_INFLATION,
            )
        }
    }

    @Redirect(
        method = ["extractRenderState"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;" +
                "shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z",
        ),
    )
    protected fun shouldSkysoftEntityAppearGlowing(minecraft: Minecraft, entity: Entity): Boolean {
        val vanillaResult = minecraft.shouldEntityAppearGlowing(entity)
        val glowColor = SkysoftErrorBoundary.value<Int?>("Entity Highlight glow state", null) {
            skysoftGetGlowColor(entity)
        }
        return glowColor != null || vanillaResult
    }

    @Redirect(
        method = ["extractRenderState"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I",
        ),
    )
    protected fun skysoftGetTeamColor(entity: Entity): Int {
        val vanillaColor = entity.teamColor
        return SkysoftErrorBoundary.value<Int?>("Entity Highlight glow color", null) {
            skysoftGetGlowColor(entity)
        } ?: vanillaColor
    }

    private fun skysoftGetGlowColor(entity: Entity): Int? =
        (entity as? LivingEntity)?.let(EntityHighlightRenderer::getEntityGlowColor)
}
