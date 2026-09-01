package com.skysoft.events.entity

import com.skysoft.utils.ActiveListenerRegistry
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.minecraft.world.entity.Entity

object EntityLifecycleEvents {
    private val loadListeners = ActiveListenerRegistry<(Entity) -> Unit>()
    private val unloadListeners = ActiveListenerRegistry<(Entity) -> Unit>()

    fun register() {
        ClientEntityEvents.ENTITY_LOAD.register { entity, _ ->
            loadListeners.forEachActive { listener -> listener(entity) }
        }
        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            unloadListeners.forEachActive { listener -> listener(entity) }
        }
    }

    fun onLoad(
        boundary: String,
        isActive: () -> Boolean,
        listener: (Entity) -> Unit,
    ) {
        loadListeners.register(boundary, isActive, listener)
    }

    fun onUnload(
        boundary: String,
        isActive: () -> Boolean,
        listener: (Entity) -> Unit,
    ) {
        unloadListeners.register(boundary, isActive, listener)
    }
}
