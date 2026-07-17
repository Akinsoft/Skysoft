package com.skysoft.events.sound

import com.skysoft.utils.WorldVec
import com.skysoft.utils.SkysoftErrorBoundary
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
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
    private val event: Event<ReceiveSoundCallback> =
        EventFactory.createArrayBacked(ReceiveSoundCallback::class.java) { listeners ->
            ReceiveSoundCallback { event -> listeners.forEach { it.onReceiveSound(event) } }
        }

    fun register(boundary: String, listener: ReceiveSoundCallback) {
        event.register { event ->
            SkysoftErrorBoundary.run(boundary) { listener.onReceiveSound(event) }
        }
    }

    fun dispatch(sound: ClientSoundEvent) {
        event.invoker().onReceiveSound(sound)
    }
}
