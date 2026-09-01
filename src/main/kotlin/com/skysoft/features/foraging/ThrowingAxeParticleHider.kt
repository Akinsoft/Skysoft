package com.skysoft.features.foraging

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ClientEntitySnapshot
import com.skysoft.events.particle.ClientParticleEvent
import com.skysoft.events.particle.ClientParticleEvents
import com.skysoft.utils.toWorldVec
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.decoration.ArmorStand

object ThrowingAxeParticleHider {
    fun register() {
        ClientParticleEvents.register(
            "Hide Axe Particles",
            isActive = { SkysoftConfigGui.config().foraging.hideAxeParticles },
            listener = ::isThrowingAxeParticle,
        )
    }

    private fun isThrowingAxeParticle(event: ClientParticleEvent): Boolean {
        if (!isThrowingAxeTrailPacket(event)) return false
        val minecraft = Minecraft.getInstance()
        if (minecraft.player?.mainHandItem?.isThrowingAxe() == true) return true
        return ClientEntitySnapshot.entities().asSequence()
            .filterIsInstance<ArmorStand>()
            .any { armorStand ->
                armorStand.isInvisible && armorStand.mainHandItem.isThrowingAxe() &&
                    armorStand.position().toWorldVec().distanceSq(event.location) <= AXE_PARTICLE_DISTANCE_SQUARED
            }
    }

    private const val AXE_PARTICLE_DISTANCE_SQUARED = 16.0
}

internal fun isThrowingAxeTrailPacket(event: ClientParticleEvent): Boolean =
    event.type == ParticleTypes.WAX_ON && event.count == 2 && event.speed == 1.0F &&
        event.offset.lengthSq() == 0.0 && !event.longDistance
