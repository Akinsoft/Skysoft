package com.skysoft.features.event.diana

import com.skysoft.utils.ChangeResult
import com.skysoft.utils.WorldVec

internal object DianaBurrowTargetTracker {
    private val targets = mutableMapOf<String, DianaBurrowTarget>()
    private var changeListener: ((List<DianaBurrowTarget>, Long) -> Unit)? = null
    private var nextTargetId = 0L

    fun snapshot(): List<DianaBurrowTarget> = targets.values.toList()

    fun hasTargets(): Boolean = targets.isNotEmpty()

    fun setChangeListener(listener: ((List<DianaBurrowTarget>, Long) -> Unit)?) {
        changeListener = listener
    }

    fun restore(restoredTargets: List<DianaBurrowTarget>) {
        targets.clear()
        nextTargetId = 0L
        restoredTargets.forEach { target ->
            targets[target.key()] = target
            if (target.targetId > nextTargetId) nextTargetId = target.targetId
        }
    }

    fun addDetected(location: WorldVec, type: DianaBurrowType, now: Long = System.currentTimeMillis()) {
        DianaRemovedBurrows.prune(now)
        val block = location.roundToBlock()
        if (DianaRemovedBurrows.isSuppressed(block, now)) return
        put(block, type, DianaBurrowSource.DETECTED, now)
    }

    fun refreshDetected(location: WorldVec, type: DianaBurrowType, now: Long = System.currentTimeMillis()): ChangeResult {
        val block = location.roundToBlock()
        val key = block.blockKey()
        val current = targets[key] ?: return ChangeResult.UNCHANGED
        if (current.source != DianaBurrowSource.DETECTED || current.type != type) return ChangeResult.UNCHANGED
        if (now - current.updatedAtMillis < DETECTED_REFRESH_MILLIS) return ChangeResult.UNCHANGED
        targets[key] = current.copy(updatedAtMillis = now)
        return ChangeResult.CHANGED
    }

    fun trackGuess(
        location: WorldVec,
        now: Long = System.currentTimeMillis(),
        replacing: DianaBurrowTarget? = null,
    ): DianaBurrowTarget? {
        DianaRemovedBurrows.prune(now)
        val block = location.roundToBlock()
        if (!DianaRemovedBurrows.isAllowed(block, now)) return null
        val replacementCreatedAtMillis = removeReplacementGuess(replacing, block, now)
        val existingAtBlock = targets[block.blockKey()]
        if (existingAtBlock != null && existingAtBlock.source != DianaBurrowSource.GUESS) {
            return existingAtBlock
        }

        return put(
            location = block,
            type = DianaBurrowType.GUESS,
            source = DianaBurrowSource.GUESS,
            now = now,
            createdAtMillis = replacementCreatedAtMillis,
        )
    }

    fun currentTarget(playerLocation: WorldVec?): DianaBurrowTarget? {
        val values = targets.values
        if (playerLocation == null) return values.firstOrNull()
        return values.minByOrNull { it.location.blockCenter().distanceSq(playerLocation) }
    }

    fun targetAt(location: WorldVec): DianaBurrowTarget? =
        targets[location.roundToBlock().blockKey()]

    fun removeAt(
        location: WorldVec,
        now: Long = System.currentTimeMillis(),
    ): DianaBurrowTarget? {
        val target = targetAt(location) ?: return null
        return removeTarget(target, now)
    }

    fun removeIfCurrent(
        target: DianaBurrowTarget,
        now: Long = System.currentTimeMillis(),
        suppress: Boolean = true,
    ): DianaBurrowTarget? {
        val current = targetAt(target.location) ?: return null
        if (current.targetId != target.targetId) return null
        return removeTarget(current, now, suppress)
    }

    fun removeGuessIfCurrentNear(
        target: DianaBurrowTarget,
        location: WorldVec,
        maxDistance: Double,
        now: Long = System.currentTimeMillis(),
    ): DianaBurrowTarget? {
        val current = targetAt(target.location) ?: return null
        if (current.targetId != target.targetId || current.source != DianaBurrowSource.GUESS) return null
        if (current.location.distance(location.roundToBlock()) > maxDistance) return null
        return removeTarget(current, now, suppress = false)
    }

    fun prune(now: Long = System.currentTimeMillis()) {
        DianaRemovedBurrows.prune(now)
    }

    fun clear(persist: Boolean = false, now: Long = System.currentTimeMillis()) {
        targets.clear()
        nextTargetId = 0L
        DianaRemovedBurrows.clear()
        if (persist) notifyChanged(now)
    }

    private fun put(
        location: WorldVec,
        type: DianaBurrowType,
        source: DianaBurrowSource,
        now: Long,
        createdAtMillis: Long? = null,
    ): DianaBurrowTarget {
        val block = location.roundToBlock()
        val key = block.blockKey()
        val oldTarget = targets[key]
        val sameLogicalTarget = oldTarget?.isSameLogicalTarget(type, source) == true
        val target = DianaBurrowTarget(
            targetId = if (sameLogicalTarget) oldTarget.targetId else ++nextTargetId,
            location = block,
            type = type,
            source = source,
            createdAtMillis = createdAtMillis ?: oldTarget?.takeIf { sameLogicalTarget }?.createdAtMillis ?: now,
            updatedAtMillis = now,
        )
        targets[key] = target
        if (oldTarget == null || oldTarget.type != type || oldTarget.source != source) {
            notifyChanged(now)
        }
        return target
    }

    private fun removeTarget(
        target: DianaBurrowTarget,
        now: Long,
        suppress: Boolean = true,
    ): DianaBurrowTarget {
        targets.remove(target.key())
        if (suppress) DianaRemovedBurrows.suppress(target, now)
        notifyChanged(now)
        return target
    }

    private fun removeReplacementGuess(target: DianaBurrowTarget?, replacementBlock: WorldVec, now: Long): Long? {
        target ?: return null
        if (target.location == replacementBlock) return target.createdAtMillis
        val current = targetAt(target.location) ?: return null
        if (current.targetId != target.targetId || current.source != DianaBurrowSource.GUESS) return null
        targets.remove(current.key())
        notifyChanged(now)
        return current.createdAtMillis
    }

    private fun notifyChanged(now: Long) {
        changeListener?.invoke(snapshot(), now)
    }

    private const val DETECTED_REFRESH_MILLIS = 1_000L
}

private fun DianaBurrowTarget.isSameLogicalTarget(
    type: DianaBurrowType,
    source: DianaBurrowSource,
): Boolean =
    (this.type == type && this.source == source) ||
        this.type == DianaBurrowType.GUESS &&
        this.source == DianaBurrowSource.GUESS &&
        source == DianaBurrowSource.DETECTED

private fun DianaBurrowTarget.key(): String = location.blockKey()
