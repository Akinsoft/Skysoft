package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.skysoft.events.sound.ClientSoundEvent;
import com.skysoft.events.sound.ClientSoundEvents;
import com.skysoft.utils.WorldVec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class SoundPacketMixin {
    @Inject(method = "handleSoundEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    protected void skysoftPostReceiveSoundEvent(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (!ClientSoundEvents.INSTANCE.hasActiveListeners()) return;
        MixinErrorBoundary.run("Sound packet dispatch", () -> ClientSoundEvents.INSTANCE.dispatch(new ClientSoundEvent(packet.getSound().value(), packet.getSource(), new WorldVec(packet.getX(), packet.getY(), packet.getZ()), null, packet.getVolume(), packet.getPitch(), packet.getSeed())));
    }

    @Inject(method = "handleSoundEntityEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    protected void skysoftPostReceiveEntitySoundEvent(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        if (!ClientSoundEvents.INSTANCE.hasActiveListeners()) return;
        MixinErrorBoundary.run("Entity sound packet dispatch", () -> {
            Entity entity = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(packet.getId());
            ClientSoundEvents.INSTANCE.dispatch(new ClientSoundEvent(packet.getSound().value(), packet.getSource(), entity == null ? null : new WorldVec(entity.getX(), entity.getY(), entity.getZ()), packet.getId(), packet.getVolume(), packet.getPitch(), packet.getSeed()));
        });
    }
}
