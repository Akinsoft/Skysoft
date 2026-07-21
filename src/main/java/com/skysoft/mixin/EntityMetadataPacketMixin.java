package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.skysoft.events.entity.ClientEntityMetadataEvent;
import com.skysoft.events.entity.ClientEntityMetadataEvents;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class EntityMetadataPacketMixin {
    @Inject(method = "handleSetEntityData", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    protected void skysoftPostReceiveEntityMetadataEvent(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        if (!ClientEntityMetadataEvents.INSTANCE.hasActiveListeners()) return;
        MixinErrorBoundary.run("Entity metadata packet dispatch", () -> ClientEntityMetadataEvents.INSTANCE.dispatch(new ClientEntityMetadataEvent(packet.id(), packet.packedItems())));
    }
}
