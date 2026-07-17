package com.skysoft.events.entity

import com.skysoft.utils.SkysoftErrorBoundary
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.world.entity.Entity

fun interface EntityLoadCallback {
    fun onEntityLoad(entity: Entity)
}

fun interface EntityUnloadCallback {
    fun onEntityUnload(entity: Entity)
}

object EntityLifecycleEvents {
    private val load: Event<EntityLoadCallback> = EventFactory.createArrayBacked(EntityLoadCallback::class.java) { listeners ->
        EntityLoadCallback { entity ->
            listeners.forEach { it.onEntityLoad(entity) }
        }
    }

    private val unload: Event<EntityUnloadCallback> = EventFactory.createArrayBacked(EntityUnloadCallback::class.java) { listeners ->
        EntityUnloadCallback { entity ->
            listeners.forEach { it.onEntityUnload(entity) }
        }
    }

    fun register() {
        ClientEntityEvents.ENTITY_LOAD.register { entity, _ -> load.invoker().onEntityLoad(entity) }
        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ -> unload.invoker().onEntityUnload(entity) }
    }

    fun onLoad(boundary: String, listener: EntityLoadCallback) {
        load.register { entity ->
            SkysoftErrorBoundary.run(boundary) { listener.onEntityLoad(entity) }
        }
    }

    fun onUnload(boundary: String, listener: EntityUnloadCallback) {
        unload.register { entity ->
            SkysoftErrorBoundary.run(boundary) { listener.onEntityUnload(entity) }
        }
    }
}
