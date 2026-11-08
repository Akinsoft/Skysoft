package com.skysoft.mixin;

import com.skysoft.events.entity.ClientEntityMetadataEvent;
import com.skysoft.events.entity.ClientEntityMetadataEvents;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class EntityMetadataPacketMixin {
    @Inject(method = "handleSetEntityData", at = @At(value = "INVOKE", target = PacketHandlerTargetsKt.PACKET_THREAD_CHECK_TARGET, shift = At.Shift.AFTER))
    private void skysoftPostReceiveEntityMetadataEvent(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        if (!ClientEntityMetadataEvents.INSTANCE.hasActiveListeners()) return;
        MixinErrorBoundary.run("Entity metadata packet dispatch", () -> ClientEntityMetadataEvents.INSTANCE.dispatch(new ClientEntityMetadataEvent(packet.id(), packet.packedItems())));
    }
}
