package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.skysoft.features.helditem.HeldItemSwing;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntitySwingMixin {
    @ModifyExpressionValue(
        method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getModifiedSwingDuration(Lnet/minecraft/world/item/component/SwingAnimation;)I"
        )
    )
    private int skysoftModifyHeldItemSwingDuration(
        int original,
        @Local(argsOnly = true) InteractionHand hand,
        @Local(argsOnly = true) SwingAnimation animation
    ) {
        return HeldItemSwing.duration((LivingEntity) (Object) this, hand, animation.duration(), original);
    }
}
