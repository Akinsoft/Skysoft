package com.skysoft.events.sound

import com.skysoft.events.ActiveEvents
import com.skysoft.utils.WorldVec
import com.skysoft.utils.location
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

class ClientSoundEvent(
    val sound: SoundEvent,
    val source: SoundSource,
    val location: WorldVec?,
    val entityId: Int?,
    val volume: Float,
    val pitch: Float,
    val seed: Long,
)

internal object ClientSoundEvents : ActiveEvents<(ClientSoundEvent) -> Unit>() {
    fun dispatch(sound: ClientSoundEvent) {
        listeners.forEachActive { listener -> listener(sound) }
    }

    fun dispatch(packet: ClientboundSoundPacket) {
        dispatch(
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

    fun dispatch(packet: ClientboundSoundEntityPacket) {
        dispatch(
            ClientSoundEvent(
                packet.sound.value(),
                packet.source,
                Minecraft.getInstance().level?.getEntity(packet.id)?.location,
                packet.id,
                packet.volume,
                packet.pitch,
                packet.seed,
            ),
        )
    }
}
