package com.skysoft.events.sound

import com.skysoft.utils.WorldVec
import com.skysoft.utils.SkysoftErrorBoundary
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
    private var listeners: List<ActiveSoundListener> = emptyList()

    fun register(
        boundary: String,
        isActive: () -> Boolean,
        listener: ReceiveSoundCallback,
    ) {
        listeners += ActiveSoundListener(boundary, isActive, listener)
    }

    fun hasActiveListeners(): Boolean = listeners.any { it.isActive() }

    fun dispatch(sound: ClientSoundEvent) {
        listeners.forEach { listener ->
            if (listener.isActive()) {
                SkysoftErrorBoundary.run(listener.boundary) { listener.callback.onReceiveSound(sound) }
            }
        }
    }

    private data class ActiveSoundListener(
        val boundary: String,
        val isActive: () -> Boolean,
        val callback: ReceiveSoundCallback,
    )
}
