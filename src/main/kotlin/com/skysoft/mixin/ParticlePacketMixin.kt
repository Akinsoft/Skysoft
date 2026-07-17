package com.skysoft.mixin

import com.skysoft.events.particle.ClientParticleEvent
import com.skysoft.events.particle.ClientParticleEvents
import com.skysoft.utils.WorldVec
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ClientPacketListener::class)
open class ParticlePacketMixin {
    @Inject(
        method = ["handleParticleEvent"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(" +
                    "Lnet/minecraft/network/protocol/Packet;" +
                    "Lnet/minecraft/network/PacketListener;" +
                    "Lnet/minecraft/network/PacketProcessor;)V",
                shift = At.Shift.AFTER,
            ),
        ],
        cancellable = true,
    )
    protected fun skysoftPostReceiveParticleEvent(packet: ClientboundLevelParticlesPacket, ci: CallbackInfo) {
        val cancelled = SkysoftErrorBoundary.value("Particle packet dispatch", false) {
            ClientParticleEvents.shouldCancelParticle(
                ClientParticleEvent(
                    packet.particle.type,
                    WorldVec(packet.x, packet.y, packet.z),
                    packet.count,
                    packet.maxSpeed,
                    WorldVec(packet.xDist.toDouble(), packet.yDist.toDouble(), packet.zDist.toDouble()),
                    packet.isOverrideLimiter,
                ),
            )
        }
        if (cancelled) ci.cancel()
    }
}
