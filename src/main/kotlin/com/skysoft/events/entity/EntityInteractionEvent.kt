package com.skysoft.events.entity

import com.skysoft.data.InteractionClick
import com.skysoft.utils.ActiveListenerRegistry
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
    private val listeners = ActiveListenerRegistry<EntityClickCallback>()

    fun register(
        boundary: String,
        isActive: () -> Boolean,
        listener: EntityClickCallback,
    ) {
        listeners.register(boundary, isActive, listener)
    }

    fun hasActiveListeners(): Boolean = listeners.hasActiveListeners

    fun shouldCancelEntityClick(entityClick: EntityInteractionEvent): Boolean =
        listeners.anyActive { listener -> listener.shouldCancelEntityClick(entityClick) }
}
