package com.skysoft.events.entity

import com.skysoft.data.InteractionClick
import com.skysoft.utils.SkysoftErrorBoundary
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.world.entity.Entity

class EntityInteractionEvent(
    val clickType: InteractionClick,
    val action: ActionType,
    val clickedEntity: Entity,
) {
    enum class ActionType {
        INTERACT,
        ATTACK,
        INTERACT_AT,
    }
}

fun interface EntityClickCallback {
    fun shouldCancelEntityClick(event: EntityInteractionEvent): Boolean
}

object EntityInteractionEvents {
    private val event: Event<EntityClickCallback> = EventFactory.createArrayBacked(EntityClickCallback::class.java) { listeners ->
        EntityClickCallback { event ->
            listeners.any { it.shouldCancelEntityClick(event) }
        }
    }
    private var activePredicates: List<() -> Boolean> = emptyList()

    fun register(
        boundary: String,
        isActive: () -> Boolean,
        listener: EntityClickCallback,
    ) {
        activePredicates += isActive
        event.register { event ->
            isActive() && SkysoftErrorBoundary.value(boundary, false) { listener.shouldCancelEntityClick(event) }
        }
    }

    fun hasActiveListeners(): Boolean = activePredicates.any { it() }

    fun shouldCancelEntityClick(entityClick: EntityInteractionEvent): Boolean =
        event.invoker().shouldCancelEntityClick(entityClick)
}
