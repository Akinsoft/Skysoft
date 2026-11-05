package com.skysoft.features.event.diana

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.skyblock.SkyBlockPlayerDeathParser
import com.skysoft.events.input.BlockInteractionEvent
import com.skysoft.events.input.BlockInteractionEvents
import com.skysoft.features.event.diana.DianaEventState.isDianaSpade
import com.skysoft.utils.PlayerUtilities.localPlayerLocation
import com.skysoft.utils.WorldVec
import com.skysoft.utils.chat.ChatMessage

internal object DianaBurrowInteractions {
    private val config get() = SkysoftConfigGui.config().events.diana
    private val pendingClicks = mutableListOf<PendingBurrowClick>()
    private val activeMobBurrowIds = mutableSetOf<Long>()

    fun register() {
        BlockInteractionEvents.register(
            "Diana Burrow block interaction",
            isActive = { config.burrowHelper.enabled && DianaEventState.isOnHub() },
        ) { event ->
            onBlockClick(event).shouldCancel
        }
    }

    fun onMessage(message: ChatMessage) {
        if (!DianaEventState.isOnHub()) return
        val text = message.cleanText
        when {
            text.startsWith(BURROW_CHAIN_FINISHED_MESSAGE) -> finishInteractedChain()
            text.startsWith(BURROW_DUG_MESSAGE) -> {
                val now = System.currentTimeMillis()
                val playerLocation = localPlayerLocation()
                val completed = completeProgressBurrow(now, playerLocation)
                val progress = parseDianaBurrowProgress(text)
                DianaBurrowChainState.onProgress(progress, completed, now)
                DianaArrowGuess.markBurrowRelatedMessage(
                    anchor = completed?.location,
                    now = now,
                    progress = progress,
                    clearCurrentRadius = NEW_BURROW_STEP_CLEAR_RADIUS,
                )
            }
            text == "Couldn't find an appropriate burrow, try again!" -> pendingClicks.removeLastOrNull()
            text == "Question" || text == "Click your response to answer!" -> extendPendingClicksForQuestion()
            text == "Defeat all the burrow defenders in order to dig it!" || MOB_SPAWN_PATTERN.matches(text) ->
                markInteractedMobBurrow(System.currentTimeMillis(), localPlayerLocation())
            TREASURE_PATTERN.matches(text) -> {
                val now = System.currentTimeMillis()
                val completed = completeTreasureBurrow(now, localPlayerLocation())
                DianaBurrowChainState.onTreasureCompleted(completed, now)
            }
            SkyBlockPlayerDeathParser.isLocalDeath(text) ->
                removeActiveMobBurrow(System.currentTimeMillis(), localPlayerLocation())
        }
    }

    fun onTick(now: Long) {
        DianaNonSpadeGuessBreaks.onTick(now)
        val expiredClicks = pendingClicks.filter { now >= it.expiresAtMillis }
        pendingClicks.removeAll(expiredClicks)
        val activeMobIds = activeMobTargets(activeMobBurrowIds).mapTo(mutableSetOf()) { target -> target.targetId }
        expiredClicks.forEach { click ->
            when {
                click.kind == PendingBurrowClickKind.MOB_VALIDATION -> {
                    val current = click.target.currentExactTarget() ?: return@forEach
                    if (current.type != DianaBurrowType.MOB) return@forEach
                    if (current.targetId in activeMobIds) return@forEach
                    if (DianaBurrowParticleDetector.hasRecentBurrowAt(current.location, now)) return@forEach
                    removeActiveMobBurrow(now, target = current)
                }
                click.target.type != DianaBurrowType.MOB || click.target.targetId !in activeMobIds -> {
                    removeOrAdvanceRejectedGuess(click.target, now)
                }
            }
        }
    }

    fun hasPendingClick(target: DianaBurrowTarget): Boolean =
        pendingClicks.any { click -> click.target.targetId == target.targetId }

    fun clearPendingClicks(target: DianaBurrowTarget) {
        pendingClicks.removeAll { click -> click.target.targetId == target.targetId }
    }

    fun clickProgress(target: DianaBurrowTarget): DianaBurrowClickProgress? {
        DianaBurrowChainState.clickProgress(target)?.let { return it }
        val completedClicks = if (target.targetId in activeMobBurrowIds) 1 else 0
        val requiredClicks = target.requiredClicks() ?: return null
        return DianaBurrowClickProgress(completedClicks, requiredClicks)
    }

    fun clear() {
        pendingClicks.clear()
        DianaNonSpadeGuessBreaks.clear()
        activeMobBurrowIds.clear()
        DianaBurrowChainState.clear()
    }

    private fun onBlockClick(event: BlockInteractionEvent): DianaBlockClickResult {
        val block = event.position.roundToBlock()
        val target = DianaBurrowTargetTracker.targetAt(block) ?: return DianaBlockClickResult.ALLOW
        val now = System.currentTimeMillis()
        if (!event.itemInHand.isDianaSpade()) {
            return DianaNonSpadeGuessBreaks.onBlockClick(event, target, now)
        }
        if (hasPendingClick(target)) return DianaBlockClickResult.ALLOW
        val kind = PendingBurrowClickKind.forTarget(target)
        pendingClicks += PendingBurrowClick(target, now + kind.timeoutMillis, kind)
        return DianaBlockClickResult.ALLOW
    }

    private fun completeProgressBurrow(
        now: Long,
        playerLocation: WorldVec?,
    ): DianaBurrowTarget? {
        val pending = removeLastPendingClick(now)
        if (pending == null) {
            return removeActiveMobBurrow(now, playerLocation)
                ?: removeNearbyProgressBurrow(now, playerLocation)
        }
        if (pending.target.targetId !in activeMobBurrowIds) {
            removeActiveMobBurrow(now, playerLocation)?.let { activeMob ->
                pendingClicks += pending
                return activeMob
            }
        }

        val removed = DianaBurrowTargetTracker.removeIfCurrent(pending.target, now)
        if (removed != null) {
            activeMobBurrowIds.remove(removed.targetId)
        }
        if (removed == null && pending.target.targetId in activeMobBurrowIds) {
            return removeActiveMobBurrow(now, target = pending.target)
        }
        return removed
    }

    private fun removeOrAdvanceRejectedGuess(target: DianaBurrowTarget, now: Long) {
        if (DianaArrowGuess.handleRejectedGuess(target, now) == ArrowGuessActionResult.HANDLED) return
        val current = target.currentExactTarget()
        if (target.source == DianaBurrowSource.GUESS && current?.source == DianaBurrowSource.DETECTED) return
        DianaBurrowTargetTracker.removeIfCurrent(target, now)
    }

    private fun removeNearbyProgressBurrow(
        now: Long,
        playerLocation: WorldVec?,
    ): DianaBurrowTarget? {
        val target = nearestTargetNear(
            location = playerLocation,
            types = PROGRESS_MESSAGE_TARGET_TYPES,
            maxDistance = UNMATCHED_CHAT_TARGET_DISTANCE,
        ) ?: return null
        return DianaBurrowTargetTracker.removeIfCurrent(target, now)
    }

    private fun markInteractedMobBurrow(now: Long, playerLocation: WorldVec?) {
        val pendingTarget = removeLastPendingClick(now)?.target
        val target = pendingTarget?.currentClickTarget(MOB_MESSAGE_TARGET_TYPES)
            ?: nearestTargetNear(playerLocation, MOB_MESSAGE_TARGET_TYPES, UNMATCHED_CHAT_TARGET_DISTANCE)
            ?: return
        val location = target.location
        DianaBurrowTargetTracker.addDetected(location, DianaBurrowType.MOB)
        val mob = DianaBurrowTargetTracker.targetAt(location) ?: return
        activeMobBurrowIds += mob.targetId
    }

    private fun removeActiveMobBurrow(
        now: Long,
        playerLocation: WorldVec? = null,
        target: DianaBurrowTarget? = null,
    ): DianaBurrowTarget? {
        val targetToRemove = target
            ?: activeMobTargetNear(activeMobBurrowIds, playerLocation)
            ?: activeMobTargets(activeMobBurrowIds).singleOrNull()
            ?: return null
        val current = DianaBurrowTargetTracker.snapshot()
            .firstOrNull { candidate -> candidate.targetId == targetToRemove.targetId && candidate.type == DianaBurrowType.MOB }
            ?: run {
                activeMobBurrowIds.remove(targetToRemove.targetId)
                return null
            }
        val removed = DianaBurrowTargetTracker.removeIfCurrent(targetToRemove, now)
            ?: DianaBurrowTargetTracker.removeIfCurrent(current, now)
        activeMobBurrowIds.remove(current.targetId)
        pendingClicks.removeAll { it.target.targetId == current.targetId }
        return removed
    }

    private fun completeTreasureBurrow(now: Long, playerLocation: WorldVec?): DianaBurrowTarget? {
        val pendingTarget = removeLastPendingClick(now)?.target
        val target = pendingTarget?.currentExactTarget()
            ?: pendingTarget?.currentClickTarget(TREASURE_MESSAGE_TARGET_TYPES)
            ?: nearestTargetNear(
                playerLocation ?: pendingTarget?.location?.blockCenter(),
                TREASURE_MESSAGE_TARGET_TYPES,
                UNMATCHED_CHAT_TARGET_DISTANCE,
            )
            ?: return null
        val removed = DianaBurrowTargetTracker.removeIfCurrent(target, now, suppress = false)
        if (removed != null) activeMobBurrowIds.remove(removed.targetId)
        return removed
    }

    private fun extendPendingClicksForQuestion(now: Long = System.currentTimeMillis()) {
        val expiresAt = now + QUESTION_CLICK_CLEAR_MILLIS
        pendingClicks.forEach { click ->
            if (click.expiresAtMillis < expiresAt) click.expiresAtMillis = expiresAt
        }
    }

    private fun finishInteractedChain() {
        val now = System.currentTimeMillis()
        val playerLocation = localPlayerLocation()
        val completed = completeProgressBurrow(now, playerLocation)
        DianaBurrowChainState.onProgress(
            progress = DianaBurrowProgress(completed = 10, total = 10),
            completedTarget = completed,
            now = now,
        )
        DianaArrowGuess.clearCurrentGuess(playerLocation, now)
    }

    private fun removeLastPendingClick(now: Long): PendingBurrowClick? {
        pendingClicks.removeAll { click -> now >= click.expiresAtMillis }
        return pendingClicks.removeLastOrNull()
    }

    private data class PendingBurrowClick(
        val target: DianaBurrowTarget,
        var expiresAtMillis: Long,
        val kind: PendingBurrowClickKind = PendingBurrowClickKind.NORMAL,
    )

    private enum class PendingBurrowClickKind(
        val timeoutMillis: Long,
    ) {
        NORMAL(UNANSWERED_CLICK_CLEAR_MILLIS),
        MOB_VALIDATION(MOB_CLICK_VALIDATION_MILLIS),
        ;

        companion object {
            fun forTarget(target: DianaBurrowTarget): PendingBurrowClickKind =
                if (target.type == DianaBurrowType.MOB) MOB_VALIDATION else NORMAL
        }
    }

    private const val UNANSWERED_CLICK_CLEAR_MILLIS = 5_000L
    private const val MOB_CLICK_VALIDATION_MILLIS = 2_000L
    private const val QUESTION_CLICK_CLEAR_MILLIS = 60_000L
}

private fun nearestTargetNear(
    location: WorldVec?,
    types: Set<DianaBurrowType>,
    maxDistance: Double,
): DianaBurrowTarget? {
    location ?: return null
    return DianaBurrowTargetTracker.snapshot()
        .filter { candidate -> candidate.type in types }
        .nearestWithin(location, maxDistance)
}

private fun Collection<DianaBurrowTarget>.nearestWithin(location: WorldVec, maxDistance: Double): DianaBurrowTarget? =
    nearestTo(location)?.takeIf { target -> target.location.blockCenter().distance(location) <= maxDistance }

private fun DianaBurrowTarget.currentClickTarget(types: Set<DianaBurrowType>): DianaBurrowTarget? =
    DianaBurrowTargetTracker.targetAt(location)
        ?.takeIf { target -> target.type in types }

private fun DianaBurrowTarget.currentExactTarget(): DianaBurrowTarget? =
    DianaBurrowTargetTracker.targetAt(location)
        ?.takeIf { target -> target.targetId == targetId }

private fun activeMobTargetNear(activeMobBurrowIds: MutableSet<Long>, playerLocation: WorldVec?): DianaBurrowTarget? {
    playerLocation ?: return null
    return activeMobTargets(activeMobBurrowIds).nearestWithin(playerLocation, UNMATCHED_CHAT_TARGET_DISTANCE)
}

private fun activeMobTargets(activeMobBurrowIds: MutableSet<Long>): List<DianaBurrowTarget> {
    val targets = DianaBurrowTargetTracker.snapshot()
        .filter { target -> target.type == DianaBurrowType.MOB && target.targetId in activeMobBurrowIds }
    activeMobBurrowIds.retainAll(targets.mapTo(mutableSetOf()) { target -> target.targetId })
    return targets
}

private val MOB_SPAWN_PATTERN =
    Regex("""^(?:Oh|Uh oh|Yikes|Oi|Good Grief|Danger|Woah)! You dug out (?:a )?.+!.*$""")
private val TREASURE_PATTERN =
    Regex("""^(?:RARE DROP!|Wow!) You dug out(?: a)? .+!.*$""")
private const val UNMATCHED_CHAT_TARGET_DISTANCE = 8.0
private val MOB_MESSAGE_TARGET_TYPES = setOf(DianaBurrowType.MOB, DianaBurrowType.GUESS)
private val TREASURE_MESSAGE_TARGET_TYPES = setOf(DianaBurrowType.TREASURE, DianaBurrowType.GUESS)
private val PROGRESS_MESSAGE_TARGET_TYPES = setOf(DianaBurrowType.START, DianaBurrowType.MOB, DianaBurrowType.GUESS)
private const val NEW_BURROW_STEP_CLEAR_RADIUS = 0.0

internal data class DianaBurrowClickProgress(
    val completedClicks: Int,
    val requiredClicks: Int,
    val displayType: DianaBurrowType? = null,
) {
    val label: String = "[$completedClicks/$requiredClicks]"
}

private fun DianaBurrowTarget.requiredClicks(): Int? =
    when (type) {
        DianaBurrowType.MOB -> MOB_REQUIRED_CLICKS
        DianaBurrowType.TREASURE,
        DianaBurrowType.START,
        DianaBurrowType.GUESS,
        -> null
    }

private const val MOB_REQUIRED_CLICKS = 2
