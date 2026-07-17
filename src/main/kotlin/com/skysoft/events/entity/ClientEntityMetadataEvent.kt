package com.skysoft.events.entity

import com.skysoft.utils.SkysoftErrorBoundary
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.network.syncher.SynchedEntityData

class ClientEntityMetadataEvent(
    val entityId: Int,
    val packedItems: List<SynchedEntityData.DataValue<*>>,
)

fun interface ReceiveEntityMetadataCallback {
    fun onReceiveEntityMetadata(event: ClientEntityMetadataEvent)
}

object ClientEntityMetadataEvents {
    private val event: Event<ReceiveEntityMetadataCallback> =
        EventFactory.createArrayBacked(ReceiveEntityMetadataCallback::class.java) { listeners ->
            ReceiveEntityMetadataCallback { event ->
                listeners.forEach { it.onReceiveEntityMetadata(event) }
            }
        }

    fun register(boundary: String, listener: ReceiveEntityMetadataCallback) {
        event.register { event ->
            SkysoftErrorBoundary.run(boundary) { listener.onReceiveEntityMetadata(event) }
        }
    }

    fun dispatch(metadata: ClientEntityMetadataEvent) {
        event.invoker().onReceiveEntityMetadata(metadata)
    }
}
