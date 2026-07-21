package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinFeatureAdapters;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.skysoft.features.misc.ServerTpsProvider;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ServerInfoPacketMixin {
    @Inject(method = "handleSetTime", at = @At("TAIL"))
    protected void skysoftRecordServerTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        if (!ServerTpsProvider.INSTANCE.getHasActiveConsumers()) return;
        MixinErrorBoundary.run("Server Info time packet", () -> ServerTpsProvider.INSTANCE.recordServerTime(packet.gameTime(), System.nanoTime()));
    }

    @Inject(method = "handlePongResponse", at = @At("TAIL"))
    protected void skysoftRecordPong(ClientboundPongResponsePacket packet, CallbackInfo ci) {
        if (!MixinFeatureAdapters.isPingMeasurementActive()) return;
        long receivedAtNanos = System.nanoTime();
        MixinErrorBoundary.onClientThread("Server Info pong packet", () -> MixinFeatureAdapters.recordPong(packet.time(), receivedAtNanos));
    }
}
