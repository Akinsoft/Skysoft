package com.skysoft.mixin

import com.skysoft.features.combat.CocoonTracker
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ClientPacketListener::class)
abstract class CocoonEquipmentPacketMixin {
    @Inject(
        method = ["handleSetEquipment"],
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
    protected fun skysoftDetectCocoonEquipment(
        packet: ClientboundSetEquipmentPacket,
        ci: CallbackInfo,
    ) {
        SkysoftErrorBoundary.run("Cocoon equipment packet") {
            CocoonTracker.handleEquipment(packet.entity, packet.slots.map { it.second })
        }
    }
}
