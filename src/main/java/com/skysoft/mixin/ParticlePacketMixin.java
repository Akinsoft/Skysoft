package com.skysoft.mixin;

import com.skysoft.events.particle.ClientParticleEvents;
import com.skysoft.utils.MinecraftParticles;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ParticlePacketMixin {
    @Inject(method = "handleParticleEvent", at = @At(value = "INVOKE", target = PacketHandlerTargetsKt.PACKET_THREAD_CHECK_TARGET, shift = At.Shift.AFTER), cancellable = true)
    private void skysoftPostReceiveParticleEvent(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (!ClientParticleEvents.INSTANCE.hasActiveListeners()) return;
        boolean cancelled = MixinErrorBoundary.value("Particle packet dispatch", false, () -> ClientParticleEvents.INSTANCE.shouldCancelParticle(MinecraftParticles.event(packet)));
        if (cancelled) ci.cancel();
    }
}
