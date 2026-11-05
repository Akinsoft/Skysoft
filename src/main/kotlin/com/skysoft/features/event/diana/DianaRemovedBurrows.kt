package com.skysoft.features.event.diana

import com.skysoft.utils.WorldVec

internal object DianaRemovedBurrows {
    private val suppressedTargets = mutableMapOf<String, Long>()

    fun isSuppressed(location: WorldVec, now: Long): Boolean =
        (suppressedTargets[location.blockKey()] ?: Long.MIN_VALUE) > now

    fun suppress(target: DianaBurrowTarget, now: Long) {
        suppressedTargets[target.location.blockKey()] = now + REMOVED_TARGET_SUPPRESSION_MILLIS
    }

    fun clearSuppression(location: WorldVec) {
        suppressedTargets.remove(location.roundToBlock().blockKey())
    }

    fun prune(now: Long) {
        suppressedTargets.entries.removeIf { (_, expiresAt) -> expiresAt <= now }
    }

    fun clear() {
        suppressedTargets.clear()
    }

    private const val REMOVED_TARGET_SUPPRESSION_MILLIS = 5_000L
}
