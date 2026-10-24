package com.skysoft.utils

import com.skysoft.events.particle.ClientParticleEvent
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket

object MinecraftParticles {
    @JvmStatic
    fun event(packet: ClientboundLevelParticlesPacket): ClientParticleEvent = ClientParticleEvent(
        packet.particle.type,
        WorldVec(packet.x, packet.y, packet.z),
        packet.count,
        packet.maxSpeed,
        WorldVec(packet.xDist.toDouble(), packet.yDist.toDouble(), packet.zDist.toDouble()),
        packet.isOverrideLimiter,
    )
}
