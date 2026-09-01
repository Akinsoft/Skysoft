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

internal enum class DianaBurrowSource {
    DETECTED,
    GUESS,
}
