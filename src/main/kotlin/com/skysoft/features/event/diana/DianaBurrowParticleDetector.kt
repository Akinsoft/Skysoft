package com.skysoft.features.event.diana

import com.skysoft.events.particle.ClientParticleEvent
import com.skysoft.utils.WorldVec

internal object DianaBurrowParticleDetector {
    private val partialBurrows = mutableMapOf<String, PartialBurrow>()

    fun handle(event: ClientParticleEvent, now: Long) {
        val kind = DianaParticleClassifier.classify(event) ?: return
        val location = event.location.down().roundToBlock()
        val partial = partialBurrows.getOrPut(location.blockKey()) { PartialBurrow() }

        when (kind) {
            DianaBurrowParticleKind.ENCHANT -> partial.enchantSamples++
            DianaBurrowParticleKind.START -> partial.recordType(DianaBurrowType.START)
            DianaBurrowParticleKind.MOB -> partial.recordType(DianaBurrowType.MOB)
            DianaBurrowParticleKind.TREASURE -> partial.recordType(DianaBurrowType.TREASURE)
        }

        partial.lastSeenMillis = now
        val type = partial.type ?: return
        if (partial.typeSamples < REQUIRED_TYPE_SAMPLES || partial.enchantSamples < REQUIRED_ENCHANT_SAMPLES) return
        if (partial.confirmedType == type) {
            DianaBurrowTargetTracker.refreshDetected(location, type, now)
            return
        }

        val matchedGuess = DianaArrowGuess.onDetectedBurrow(location, now)
        val previous = DianaBurrowTargetTracker.targetAt(location)
        DianaBurrowTargetTracker.addDetected(location, type, now)
        val target = DianaBurrowTargetTracker.targetAt(location)
        if (target != null && previous.isNewLogicalTarget(type)) {
            DianaBurrowChainState.onTargetReplaced(matchedGuess, target, now)
        }
        DianaSpadeGuess.onDetectedBurrow(location, now)
        partial.confirmedType = type
    }

    fun hasRecentBurrowAt(location: WorldVec, now: Long): Boolean {
        val partial = partialBurrows[location.roundToBlock().blockKey()] ?: return false
        return now - partial.lastSeenMillis <= PARTICLE_MEMORY_MILLIS && partial.hasSignal()
    }

    fun prune(now: Long) {
        partialBurrows.entries.removeIf { (_, burrow) -> now - burrow.lastSeenMillis > PARTICLE_MEMORY_MILLIS }
    }

    fun clear() {
        partialBurrows.clear()
    }

    private class PartialBurrow(
        var enchantSamples: Int = 0,
        var type: DianaBurrowType? = null,
        var typeSamples: Int = 0,
        var confirmedType: DianaBurrowType? = null,
        var lastSeenMillis: Long = System.currentTimeMillis(),
    ) {
        fun recordType(newType: DianaBurrowType) {
            typeSamples = if (type == newType) typeSamples + 1 else 1
            type = newType
        }

        fun hasSignal(): Boolean =
            enchantSamples > 0 || typeSamples > 0 || confirmedType != null
    }

    private const val PARTICLE_MEMORY_MILLIS = 500L
    private const val REQUIRED_TYPE_SAMPLES = 2
    private const val REQUIRED_ENCHANT_SAMPLES = 2
}

private fun DianaBurrowTarget?.isNewLogicalTarget(type: DianaBurrowType): Boolean =
    this == null || this.type != type || this.source != DianaBurrowSource.DETECTED
