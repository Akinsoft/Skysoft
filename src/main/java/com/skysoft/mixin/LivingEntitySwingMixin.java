package com.skysoft.mixin;

import com.skysoft.features.helditem.HeldItemSwing;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntitySwingMixin {
    @Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
    private void skysoftModifyHeldItemSwingDuration(CallbackInfoReturnable<Integer> cir) {
        int duration = HeldItemSwing.duration((LivingEntity) (Object) this, cir.getReturnValue());
        cir.setReturnValue(duration);
    }
}
