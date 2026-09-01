package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.skysoft.features.helditem.HeldItemSwing;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntitySwingMixin {
    @ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"))
    private int skysoftModifyHeldItemSwingDuration(int original) {
        return HeldItemSwing.duration((LivingEntity) (Object) this, original);
    }
}
