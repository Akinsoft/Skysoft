package com.skysoft.events.entity

import com.skysoft.utils.ActiveListenerRegistry
import net.minecraft.network.syncher.SynchedEntityData

class ClientEntityMetadataEvent(
    val entityId: Int,
    val packedItems: List<SynchedEntityData.DataValue<*>>,
)

fun interface ReceiveEntityMetadataCallback {
    fun onReceiveEntityMetadata(event: ClientEntityMetadataEvent)
}

object ClientEntityMetadataEvents {
    private val listeners = ActiveListenerRegistry<ReceiveEntityMetadataCallback>()

    fun register(
        boundary: String,
        isActive: () -> Boolean,
        listener: ReceiveEntityMetadataCallback,
    ) {
        listeners.register(boundary, isActive, listener)
    }

    fun hasActiveListeners(): Boolean = listeners.hasActiveListeners

    fun dispatch(metadata: ClientEntityMetadataEvent) {
        listeners.forEachActive { listener -> listener.onReceiveEntityMetadata(metadata) }
    }
}
