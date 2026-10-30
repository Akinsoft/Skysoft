package com.skysoft.events.entity

import com.skysoft.events.ActiveEvents
import net.minecraft.network.syncher.SynchedEntityData

class ClientEntityMetadataEvent(
    val entityId: Int,
    val packedItems: List<SynchedEntityData.DataValue<*>>,
)

internal object ClientEntityMetadataEvents : ActiveEvents<(ClientEntityMetadataEvent) -> Unit>() {
    fun dispatch(metadata: ClientEntityMetadataEvent) {
        listeners.forEachActive { listener -> listener(metadata) }
    }
}
