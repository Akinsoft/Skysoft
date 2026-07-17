// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

package com.skysoft.utils.input

import com.skysoft.data.InteractionClick
import com.skysoft.events.entity.EntityInteractionEvent
import com.skysoft.events.entity.EntityInteractionEvents
import com.skysoft.events.input.BlockInteractionEvent
import com.skysoft.events.input.BlockInteractionEvents
import com.skysoft.events.input.ItemUseEvent
import com.skysoft.events.input.ItemUseEvents
import com.skysoft.utils.toWorldVec
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult

object InputEventInterceptor {
    @JvmStatic
    fun processRightClick(hitResult: HitResult?): InputHandlingResult =
        dispatchClick(
            hitResult,
            InteractionClick.RIGHT_CLICK,
            EntityInteractionEvent.ActionType.INTERACT_AT,
        )

    @JvmStatic
    fun processLeftClick(hitResult: HitResult?): InputHandlingResult =
        dispatchClick(
            hitResult,
            InteractionClick.LEFT_CLICK,
            EntityInteractionEvent.ActionType.ATTACK,
        )

    private fun dispatchClick(
        hitResult: HitResult?,
        clickType: InteractionClick,
        entityAction: EntityInteractionEvent.ActionType,
    ): InputHandlingResult {
        val player = Minecraft.getInstance().player
        val itemConsumed = ItemUseEvents.shouldCancelItemUse(ItemUseEvent(clickType, player?.mainHandItem))
        val targetConsumed = when (interactionTarget(hitResult?.type)) {
            InteractionTarget.BLOCK -> BlockInteractionEvents.shouldCancelBlockClick(
                BlockInteractionEvent(
                    clickType,
                    player?.mainHandItem,
                    (hitResult as BlockHitResult).blockPos.toWorldVec(),
                ),
            )
            InteractionTarget.ENTITY -> EntityInteractionEvents.shouldCancelEntityClick(
                EntityInteractionEvent(clickType, entityAction, (hitResult as EntityHitResult).entity),
            )
            InteractionTarget.NONE -> false
        }
        return if (itemConsumed || targetConsumed) InputHandlingResult.CONSUMED else InputHandlingResult.IGNORED
    }
}

internal enum class InteractionTarget {
    BLOCK,
    ENTITY,
    NONE,
}

internal fun interactionTarget(type: HitResult.Type?): InteractionTarget =
    when (type) {
        HitResult.Type.BLOCK -> InteractionTarget.BLOCK
        HitResult.Type.ENTITY -> InteractionTarget.ENTITY
        HitResult.Type.MISS, null -> InteractionTarget.NONE
    }
