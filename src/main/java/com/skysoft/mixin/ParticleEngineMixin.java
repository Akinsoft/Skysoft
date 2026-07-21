package com.skysoft.mixin;

import com.skysoft.features.pets.VisiblePetPosition;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
    @ModifyArgs(
        method = "createParticle",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;makeParticle("
                + "Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)"
                + "Lnet/minecraft/client/particle/Particle;"
        )
    )
    private void skysoftAdjustVisiblePetParticlePosition(Args args) {
        double x = (double) args.get(1);
        double y = (double) args.get(2);
        double z = (double) args.get(3);
        args.set(2, VisiblePetPosition.adjustParticleY(x, y, z));
    }
}
