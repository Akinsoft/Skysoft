package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.skysoft.events.sound.ClientSoundEvents;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class SoundPacketMixin {
    @Inject(method = "handleSoundEvent", at = @At(value = "INVOKE", target = PacketHandlerTargetsKt.PACKET_THREAD_CHECK_TARGET, shift = At.Shift.AFTER))
    protected void skysoftPostReceiveSoundEvent(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (!ClientSoundEvents.INSTANCE.hasActiveListeners()) return;
        MixinErrorBoundary.run("Sound packet dispatch", () -> ClientSoundEvents.INSTANCE.dispatch(packet));
    }

    @Inject(method = "handleSoundEntityEvent", at = @At(value = "INVOKE", target = PacketHandlerTargetsKt.PACKET_THREAD_CHECK_TARGET, shift = At.Shift.AFTER))
    protected void skysoftPostReceiveEntitySoundEvent(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        if (!ClientSoundEvents.INSTANCE.hasActiveListeners()) return;
        MixinErrorBoundary.run("Entity sound packet dispatch", () -> ClientSoundEvents.INSTANCE.dispatch(packet));
    }
}
