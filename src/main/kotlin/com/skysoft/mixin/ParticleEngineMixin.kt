package com.skysoft.mixin

import com.skysoft.features.pets.VisiblePetPosition
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.particle.ParticleEngine
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.ModifyArgs
import org.spongepowered.asm.mixin.injection.invoke.arg.Args

private const val X_ARGUMENT_INDEX = 1
private const val Y_ARGUMENT_INDEX = 2
private const val Z_ARGUMENT_INDEX = 3

@Mixin(ParticleEngine::class)
open class ParticleEngineMixin {
    @ModifyArgs(
        method = ["createParticle"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;makeParticle(" +
                "Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)" +
                "Lnet/minecraft/client/particle/Particle;",
        ),
    )
    protected fun skysoftAdjustVisiblePetParticlePosition(args: Args) {
        val x = args.get(X_ARGUMENT_INDEX) as Double
        val y = args.get(Y_ARGUMENT_INDEX) as Double
        val z = args.get(Z_ARGUMENT_INDEX) as Double
        val adjustedY = SkysoftErrorBoundary.value("Visible Pet Position particle adjustment", y) {
            VisiblePetPosition.adjustParticleY(x, y, z)
        }
        args.set(Y_ARGUMENT_INDEX, adjustedY)
    }
}
