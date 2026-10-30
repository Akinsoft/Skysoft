package com.skysoft.events.entity

import com.skysoft.data.InteractionClick
import com.skysoft.events.ActiveEvents
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

internal object EntityInteractionEvents : ActiveEvents<(EntityInteractionEvent) -> Boolean>() {
    fun shouldCancelEntityClick(entityClick: EntityInteractionEvent): Boolean =
        listeners.anyActive { listener -> listener(entityClick) }
}
