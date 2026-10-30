package com.skysoft.events.particle

import com.skysoft.events.ActiveEvents
import com.skysoft.utils.WorldVec
import net.minecraft.core.particles.ParticleType

class ClientParticleEvent(
    val type: ParticleType<*>,
    val location: WorldVec,
    val count: Int,
    val speed: Float,
    val offset: WorldVec,
    val longDistance: Boolean,
)

internal object ClientParticleEvents : ActiveEvents<(ClientParticleEvent) -> Boolean>() {
    fun shouldCancelParticle(particle: ClientParticleEvent): Boolean =
        listeners.foldActive(false) { cancelled, listener -> listener(particle) || cancelled }
}
