package com.skysoft.mixin

import com.skysoft.features.helditem.HeldItemSwing
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.world.entity.LivingEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(LivingEntity::class)
open class LivingEntitySwingMixin {
    @Inject(method = ["getCurrentSwingDuration"], at = [At("RETURN")], cancellable = true)
    protected fun skysoftModifyHeldItemSwingDuration(cir: CallbackInfoReturnable<Int>) {
        val duration = SkysoftErrorBoundary.value("Held Item swing duration", cir.returnValue) {
            HeldItemSwing.duration(this as LivingEntity, cir.returnValue)
        }
        cir.setReturnValue(duration)
    }
}
