package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.skysoft.events.particle.ClientParticleEvent;
import com.skysoft.events.particle.ClientParticleEvents;
import com.skysoft.utils.WorldVec;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ParticlePacketMixin {
    @Inject(method = "handleParticleEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER), cancellable = true)
    protected void skysoftPostReceiveParticleEvent(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (!ClientParticleEvents.INSTANCE.hasActiveListeners()) return;
        boolean cancelled = MixinErrorBoundary.value("Particle packet dispatch", false, () -> ClientParticleEvents.INSTANCE.shouldCancelParticle(
            new ClientParticleEvent(packet.getParticle().getType(), new WorldVec(packet.getX(), packet.getY(), packet.getZ()), packet.getCount(), packet.getMaxSpeed(), new WorldVec(packet.getXDist(), packet.getYDist(), packet.getZDist()), packet.isOverrideLimiter())));
        if (cancelled) ci.cancel();
    }
}
