package com.skysoft.mixin;

import com.skysoft.features.event.diana.DianaHubTerrainCache;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class DianaHubTerrainPacketMixin {
    @Shadow private ClientLevel level;

    @Inject(
        method = "handleForgetLevelChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER
        ),
        cancellable = true
    )
    private void skysoftKeepDianaHubTerrain(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        boolean retained = MixinErrorBoundary.value(
            "Diana Hub terrain retention",
            false,
            () -> DianaHubTerrainCache.didRetain(this.level, packet.pos())
        );
        if (retained) ci.cancel();
    }

    @Inject(
        method = "handleLevelChunkWithLight",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER
        )
    )
    private void skysoftRefreshDianaHubTerrain(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        MixinErrorBoundary.run(
            "Diana Hub terrain refresh",
            () -> DianaHubTerrainCache.markServerLoaded(this.level, new ChunkPos(packet.getX(), packet.getZ()))
        );
    }
}
