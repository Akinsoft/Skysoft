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
import net.minecraft.core.BlockPos
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

    @JvmStatic
    fun processContinuedLeftBlockClick(position: BlockPos): InputHandlingResult =
        handlingResult(isBlockClickCancelled(InteractionClick.LEFT_CLICK, position))

    private fun dispatchClick(
        hitResult: HitResult?,
        clickType: InteractionClick,
        entityAction: EntityInteractionEvent.ActionType,
    ): InputHandlingResult {
        val itemConsumed = ItemUseEvents.hasActiveListeners() &&
            ItemUseEvents.shouldCancelItemUse(ItemUseEvent(clickType, Minecraft.getInstance().player?.mainHandItem))
        val targetConsumed = when (hitResult?.type) {
            HitResult.Type.BLOCK -> isBlockClickCancelled(clickType, (hitResult as BlockHitResult).blockPos)
            HitResult.Type.ENTITY -> EntityInteractionEvents.hasActiveListeners() &&
                EntityInteractionEvents.shouldCancelEntityClick(
                    EntityInteractionEvent(clickType, entityAction, (hitResult as EntityHitResult).entity),
                )
            HitResult.Type.MISS, null -> false
        }
        return handlingResult(itemConsumed || targetConsumed)
    }

    private fun isBlockClickCancelled(clickType: InteractionClick, position: BlockPos): Boolean =
        BlockInteractionEvents.hasActiveListeners() &&
            BlockInteractionEvents.shouldCancelBlockClick(
                BlockInteractionEvent(clickType, Minecraft.getInstance().player?.mainHandItem, position.toWorldVec()),
            )

    private fun handlingResult(isConsumed: Boolean): InputHandlingResult =
        InputHandlingResult.from(isConsumed)
}
