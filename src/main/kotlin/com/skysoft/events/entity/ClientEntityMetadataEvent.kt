package com.skysoft.events.entity

import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.network.syncher.SynchedEntityData

class ClientEntityMetadataEvent(
    val entityId: Int,
    val packedItems: List<SynchedEntityData.DataValue<*>>,
)

fun interface ReceiveEntityMetadataCallback {
    fun onReceiveEntityMetadata(event: ClientEntityMetadataEvent)
}

object ClientEntityMetadataEvents {
    private var listeners: List<ActiveMetadataListener> = emptyList()

    fun register(
        boundary: String,
        isActive: () -> Boolean,
        listener: ReceiveEntityMetadataCallback,
    ) {
        listeners += ActiveMetadataListener(boundary, isActive, listener)
    }

    fun hasActiveListeners(): Boolean = listeners.any { it.isActive() }

    fun dispatch(metadata: ClientEntityMetadataEvent) {
        listeners.forEach { listener ->
            if (listener.isActive()) {
                SkysoftErrorBoundary.run(listener.boundary) { listener.callback.onReceiveEntityMetadata(metadata) }
            }
        }
    }

    private data class ActiveMetadataListener(
        val boundary: String,
        val isActive: () -> Boolean,
        val callback: ReceiveEntityMetadataCallback,
    )
}
