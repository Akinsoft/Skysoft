package com.skysoft.mixin

import com.skysoft.events.sound.ClientSoundEvent
import com.skysoft.events.sound.ClientSoundEvents
import com.skysoft.utils.WorldVec
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ClientPacketListener::class)
open class SoundPacketMixin {
    @Inject(
        method = ["handleSoundEvent"],
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
    )
    protected fun skysoftPostReceiveSoundEvent(packet: ClientboundSoundPacket, ci: CallbackInfo) {
        SkysoftErrorBoundary.run("Sound packet dispatch") {
            ClientSoundEvents.dispatch(
                ClientSoundEvent(
                    packet.sound.value(),
                    packet.source,
                    WorldVec(packet.x, packet.y, packet.z),
                    null,
                    packet.volume,
                    packet.pitch,
                    packet.seed,
                ),
            )
        }
    }

    @Inject(
        method = ["handleSoundEntityEvent"],
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
    )
    protected fun skysoftPostReceiveEntitySoundEvent(packet: ClientboundSoundEntityPacket, ci: CallbackInfo) {
        SkysoftErrorBoundary.run("Entity sound packet dispatch") {
            val entity = Minecraft.getInstance().level?.getEntity(packet.id)
            ClientSoundEvents.dispatch(
                ClientSoundEvent(
                    packet.sound.value(),
                    packet.source,
                    entity?.let { WorldVec(it.x, it.y, it.z) },
                    packet.id,
                    packet.volume,
                    packet.pitch,
                    packet.seed,
                ),
            )
        }
    }
}
