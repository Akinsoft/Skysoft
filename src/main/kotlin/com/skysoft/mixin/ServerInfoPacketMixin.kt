package com.skysoft.mixin

import com.skysoft.features.misc.ServerInfoDisplay
import com.skysoft.features.misc.ServerTpsProvider
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.game.ClientboundSetTimePacket
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ClientPacketListener::class)
open class ServerInfoPacketMixin {
    @Inject(method = ["handleSetTime"], at = [At("TAIL")])
    protected fun skysoftRecordServerTime(packet: ClientboundSetTimePacket, ci: CallbackInfo) {
        if (!ServerTpsProvider.hasActiveConsumers) return
        SkysoftErrorBoundary.run("Server Info time packet") {
            ServerTpsProvider.recordServerTime(packet.gameTime(), System.nanoTime())
        }
    }

    @Inject(method = ["handlePongResponse"], at = [At("TAIL")])
    protected fun skysoftRecordPong(packet: ClientboundPongResponsePacket, ci: CallbackInfo) {
        if (!ServerInfoDisplay.isPingMeasurementActive) return
        val receivedAtNanos = System.nanoTime()
        SkysoftErrorBoundary.onClientThread("Server Info pong packet") {
            ServerInfoDisplay.recordPong(packet.time(), receivedAtNanos)
        }
    }
}
