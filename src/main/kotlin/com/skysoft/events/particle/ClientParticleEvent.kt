package com.skysoft.events.particle

import com.skysoft.utils.WorldVec
import com.skysoft.utils.SkysoftErrorBoundary
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
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
    private val event: Event<ReceiveParticleCallback> =
        EventFactory.createArrayBacked(ReceiveParticleCallback::class.java, ::receiveParticleInvoker)

    fun register(boundary: String, listener: ReceiveParticleCallback) {
        event.register { event ->
            SkysoftErrorBoundary.value(boundary, false) { listener.shouldCancelParticle(event) }
        }
    }

    fun shouldCancelParticle(particle: ClientParticleEvent): Boolean = event.invoker().shouldCancelParticle(particle)
}

internal fun receiveParticleInvoker(listeners: Array<ReceiveParticleCallback>): ReceiveParticleCallback =
    ReceiveParticleCallback { event ->
        var cancelled = false
        listeners.forEach { listener ->
            cancelled = listener.shouldCancelParticle(event) || cancelled
        }
        cancelled
    }
