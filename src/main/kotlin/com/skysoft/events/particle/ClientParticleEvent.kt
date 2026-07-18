package com.skysoft.events.particle

import com.skysoft.utils.WorldVec
import com.skysoft.utils.SkysoftErrorBoundary
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
    private var listeners: List<ActiveParticleListener> = emptyList()

    fun register(
        boundary: String,
        isActive: () -> Boolean,
        listener: ReceiveParticleCallback,
    ) {
        listeners += ActiveParticleListener(boundary, isActive, listener)
    }

    fun hasActiveListeners(): Boolean = listeners.any { it.isActive() }

    fun shouldCancelParticle(particle: ClientParticleEvent): Boolean {
        var cancelled = false
        listeners.forEach { listener ->
            if (listener.isActive()) {
                cancelled = SkysoftErrorBoundary.value(listener.boundary, false) {
                    listener.callback.shouldCancelParticle(particle)
                } || cancelled
            }
        }
        return cancelled
    }

    private data class ActiveParticleListener(
        val boundary: String,
        val isActive: () -> Boolean,
        val callback: ReceiveParticleCallback,
    )
}

internal fun receiveParticleInvoker(listeners: Array<ReceiveParticleCallback>): ReceiveParticleCallback =
    ReceiveParticleCallback { event ->
        var cancelled = false
        listeners.forEach { listener ->
            cancelled = listener.shouldCancelParticle(event) || cancelled
        }
        cancelled
    }
