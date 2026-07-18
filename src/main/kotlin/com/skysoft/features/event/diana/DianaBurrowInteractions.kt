package com.skysoft.features.event.diana

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.events.input.BlockInteractionEvent
import com.skysoft.events.input.BlockInteractionEvents
import com.skysoft.features.event.diana.DianaEventState.isDianaSpade
import com.skysoft.utils.WorldVec
import com.skysoft.utils.chat.ChatMessage
import com.skysoft.utils.toWorldVec
import net.minecraft.client.Minecraft

internal object DianaBurrowInteractions {
    private val config get() = SkysoftConfigGui.config().events.diana
    private val pendingClicks = mutableListOf<PendingBurrowClick>()
    private val activeMobBurrowIds = mutableSetOf<Long>()

    fun register() {
        BlockInteractionEvents.register(
            "Diana Burrow block interaction",
            isActive = { config.enabled && DianaEventState.isOnHub() },
        ) { event ->
            onBlockClick(event).shouldCancel
        }
    }

    fun onMessage(message: ChatMessage) {
        if (!DianaEventState.isOnHub()) return

        when {
            message.cleanText.startsWith("You finished the Griffin burrow chain!") -> {
                finishInteractedChain()
            }
            message.cleanText.startsWith("You dug out a Griffin Burrow!") -> {
                val now = System.currentTimeMillis()
                val playerLocation = currentPlayerLocation()
                val completed = completeProgressBurrow(now, playerLocation)
                val progress = parseDianaBurrowProgress(message.cleanText)
                DianaBurrowChainState.onProgress(progress, completed, now)
                DianaArrowGuess.markBurrowRelatedMessage(
                    anchor = completed?.location ?: playerLocation,
                    now = now,
                    playerLocation = playerLocation,
                    progress = progress,
                    clearCurrentReason = NEW_BURROW_STEP_REASON,
                    clearCurrentRadius = NEW_BURROW_STEP_CLEAR_RADIUS,
                )
            }
            message.cleanText.startsWith("Follow the arrows") -> {
                val now = System.currentTimeMillis()
                DianaArrowGuess.markBurrowRelatedMessage(
                    anchor = null,
                    now = now,
                    playerLocation = currentPlayerLocation(),
                )
            }
            message.cleanText == "Couldn't find an appropriate burrow, try again!" ->
                pendingClicks.removeLastOrNull()
            message.cleanText == "Question" || message.cleanText == "Click your response to answer!" ->
                extendPendingClicksForQuestion()
            message.cleanText == "Defeat all the burrow defenders in order to dig it!" ->
                markInteractedMobBurrow(System.currentTimeMillis(), currentPlayerLocation())
            MOB_SPAWN_PATTERN.matches(message.cleanText) ->
                markInteractedMobBurrow(System.currentTimeMillis(), currentPlayerLocation())
            TREASURE_PATTERN.matches(message.cleanText) -> {
                val now = System.currentTimeMillis()
                val completed = completeTreasureBurrow(now, currentPlayerLocation())
                DianaBurrowChainState.onTreasureCompleted(completed, now)
            }
            message.cleanText.isOwnDianaDeathMessage() ->
                removeActiveMobBurrow(System.currentTimeMillis(), currentPlayerLocation())
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
                    if (DianaBurrowParticleDetector.hasRecentBurrowNear(
                            current.location,
                            EXACT_BURROW_PARTICLE_DISTANCE,
                            now,
                        )
                    ) {
                        return@forEach
                    }
                    removeActiveMobBurrow(now, target = current)
                }
                click.target.type != DianaBurrowType.MOB || click.target.targetId !in activeMobIds -> {
                    removeOrAdvanceRejectedGuess(click.target, now)
                }
            }
        }
    }

    fun hasPendingClick(target: DianaBurrowTarget, clear: Boolean = false): Boolean {
        val pending = pendingClicks.any { click -> click.target.targetId == target.targetId }
        if (clear) pendingClicks.removeAll { click -> click.target.targetId == target.targetId }
        return pending
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
        if (!DianaEventState.isOnHub() || !config.enabled) return DianaBlockClickResult.ALLOW
        val block = event.position.roundToBlock()
        val target = targetForClickedBlock(block) ?: return DianaBlockClickResult.ALLOW
        val now = System.currentTimeMillis()
        if (!event.itemInHand.isDianaSpade()) {
            return DianaNonSpadeGuessBreaks.onBlockClick(event, target, now)
        }
        val kind = PendingBurrowClickKind.forTarget(target)
        if (kind == PendingBurrowClickKind.MOB_VALIDATION &&
            pendingClicks.any { click -> click.kind == kind && click.target.targetId == target.targetId }
        ) {
            return DianaBlockClickResult.ALLOW
        }
        pendingClicks.removeAll { it.target.targetId == target.targetId }
        pendingClicks += PendingBurrowClick(target, now + kind.timeoutMillis, kind)
        return DianaBlockClickResult.ALLOW
    }

    private fun completeProgressBurrow(now: Long = System.currentTimeMillis()): DianaBurrowTarget? =
        completeProgressBurrow(now, currentPlayerLocation())

    private fun completeProgressBurrow(
        now: Long,
        playerLocation: WorldVec?,
    ): DianaBurrowTarget? {
        val pending = removeLastPendingClick(now)
        if (pending == null) {
            return removeActiveMobBurrow(now, playerLocation)
                ?: removeNearbyProgressBurrow(now, playerLocation)
        }

        val removed = removePendingClick(pending, now)
        if (removed != null) {
            activeMobBurrowIds.remove(removed.targetId)
        }
        if (removed == null && pending.target.targetId in activeMobBurrowIds) {
            return removeActiveMobBurrow(now, target = pending.target)
        }
        return removed
    }

    private fun removePendingClick(
        pending: PendingBurrowClick,
        now: Long = System.currentTimeMillis(),
    ): DianaBurrowTarget? {
        return DianaBurrowTargetTracker.removeIfCurrent(pending.target, now)
    }

    private fun removeOrAdvanceRejectedGuess(target: DianaBurrowTarget, now: Long) {
        if (DianaArrowGuess.handleRejectedGuess(target, now) == ArrowGuessActionResult.HANDLED) return
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
        now: Long = System.currentTimeMillis(),
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

    private fun completeTreasureBurrow(now: Long = System.currentTimeMillis()): DianaBurrowTarget? =
        completeTreasureBurrow(now, currentPlayerLocation())

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
        val playerLocation = currentPlayerLocation()
        val completed = completeProgressBurrow(now, playerLocation)
        DianaBurrowChainState.onProgress(
            progress = DianaBurrowProgress(completed = 10, total = 10),
            completedTarget = completed,
            now = now,
        )
        DianaArrowGuess.clearCurrentGuess(playerLocation, now)
    }

    private fun currentPlayerLocation(): WorldVec? =
        runCatching { Minecraft.getInstance().player?.position()?.toWorldVec() }.getOrNull()

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

internal fun targetForClickedBlock(block: WorldVec): DianaBurrowTarget? =
    DianaBurrowTargetTracker.targetAt(block)
        ?: nearestTargetNear(block.blockCenter(), ALL_BURROW_TYPES, ADJACENT_CLICK_MATCH_DISTANCE)

private fun nearestTargetNear(
    location: WorldVec?,
    types: Set<DianaBurrowType>,
    maxDistance: Double,
): DianaBurrowTarget? {
    location ?: return null
    val target = DianaBurrowTargetTracker.snapshot()
        .asSequence()
        .filter { candidate -> candidate.type in types }
        .minByOrNull { candidate -> candidate.location.blockCenter().distanceSq(location) }
        ?: return null
    if (target.location.blockCenter().distance(location) > maxDistance) return null
    return target
}

private fun DianaBurrowTarget.currentClickTarget(types: Set<DianaBurrowType>): DianaBurrowTarget? =
    DianaBurrowTargetTracker.targetAt(location)
        ?.takeIf { target -> target.type in types }
        ?: nearestTargetNear(location.blockCenter(), types, ADJACENT_CLICK_MATCH_DISTANCE)

private fun DianaBurrowTarget.currentExactTarget(): DianaBurrowTarget? =
    DianaBurrowTargetTracker.targetAt(location)
        ?.takeIf { target -> target.targetId == targetId }

private fun activeMobTargetNear(activeMobBurrowIds: MutableSet<Long>, playerLocation: WorldVec?): DianaBurrowTarget? {
    playerLocation ?: return null
    return activeMobTargets(activeMobBurrowIds)
        .minByOrNull { target -> target.location.blockCenter().distanceSq(playerLocation) }
        ?.takeIf { target -> target.location.blockCenter().distance(playerLocation) <= UNMATCHED_CHAT_TARGET_DISTANCE }
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
private const val ADJACENT_CLICK_MATCH_DISTANCE = 1.75
private const val EXACT_BURROW_PARTICLE_DISTANCE = 0.1
private const val UNMATCHED_CHAT_TARGET_DISTANCE = 8.0
private val ALL_BURROW_TYPES = DianaBurrowType.entries.toSet()
private val MOB_MESSAGE_TARGET_TYPES = setOf(DianaBurrowType.MOB, DianaBurrowType.GUESS)
private val TREASURE_MESSAGE_TARGET_TYPES = setOf(DianaBurrowType.TREASURE, DianaBurrowType.GUESS)
private val PROGRESS_MESSAGE_TARGET_TYPES = setOf(DianaBurrowType.START, DianaBurrowType.MOB, DianaBurrowType.GUESS)
private const val NEW_BURROW_STEP_REASON = "new burrow step"
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

internal fun String.isOwnDianaDeathMessage(): Boolean =
    startsWith("You died") ||
        startsWith("You were killed by ") ||
        startsWith("☠ You died") ||
        startsWith("☠ You were killed by ")

private const val MOB_REQUIRED_CLICKS = 2
