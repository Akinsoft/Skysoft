package com.skysoft.events.sound

import com.skysoft.events.ActiveEvents
import com.skysoft.utils.WorldVec
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
}
