package com.skysoft.events.particle

import com.skysoft.utils.ActiveListenerRegistry
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

fun interface ReceiveParticleCallback {
    fun shouldCancelParticle(event: ClientParticleEvent): Boolean
}

object ClientParticleEvents {
    private val listeners = ActiveListenerRegistry<ReceiveParticleCallback>()

    fun register(
        boundary: String,
        isActive: () -> Boolean,
        listener: ReceiveParticleCallback,
    ) {
        listeners.register(boundary, isActive, listener)
    }

    fun hasActiveListeners(): Boolean = listeners.hasActiveListeners

    fun shouldCancelParticle(particle: ClientParticleEvent): Boolean =
        listeners.foldActive(false) { cancelled, listener ->
            listener.shouldCancelParticle(particle) || cancelled
        }
}
