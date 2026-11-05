package com.skysoft.features.event.diana

import com.skysoft.utils.WorldVec

internal data class DianaBurrowTarget(
    val targetId: Long,
    val location: WorldVec,
    val type: DianaBurrowType,
    val source: DianaBurrowSource,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val guessCandidates: List<WorldVec> = emptyList(),
)

internal fun Collection<DianaBurrowTarget>.nearestTo(location: WorldVec): DianaBurrowTarget? =
    minByOrNull { target -> target.location.blockCenter().distanceSq(location) }

internal fun normalizeGuessCandidates(current: WorldVec, candidates: List<WorldVec>): List<WorldVec> =
    (listOf(current) + candidates)
        .map { candidate -> candidate.roundToBlock() }
        .distinctBy { candidate -> candidate.blockKey() }

internal enum class DianaBurrowSource {
    DETECTED,
    GUESS,
}
