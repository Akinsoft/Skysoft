package com.skysoft.events.sound

import com.skysoft.utils.ActiveListenerRegistry
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

fun interface ReceiveSoundCallback {
    fun onReceiveSound(event: ClientSoundEvent)
}

object ClientSoundEvents {
    private val listeners = ActiveListenerRegistry<ReceiveSoundCallback>()

    fun register(
        boundary: String,
        isActive: () -> Boolean,
        listener: ReceiveSoundCallback,
    ) {
        listeners.register(boundary, isActive, listener)
    }

    fun hasActiveListeners(): Boolean = listeners.hasActiveListeners

    fun dispatch(sound: ClientSoundEvent) {
        listeners.forEachActive { listener -> listener.onReceiveSound(sound) }
    }
}
