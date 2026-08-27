package com.skysoft.features.event.diana

import com.skysoft.utils.WorldVec

internal data class ArrowCandidateSequence(
    val sequenceId: Long,
    val targetId: Long,
    val candidates: List<ResolvedArrowCandidate>,
    val current: ResolvedArrowCandidate,
    val currentIndex: Int,
    val currentTrackedAtMillis: Long,
    val invalidatedBlockKeys: Set<String>,
    val missingParticlesFirstCheckAtMillis: Long?,
)

internal data class ArrowSequenceMatch(
    val targetId: Long,
    val sequence: ArrowCandidateSequence,
    val currentGuess: DianaBurrowTarget,
    val matchIndex: Int,
    val matchCandidate: ResolvedArrowCandidate,
    val matchDistance: Double,
    val currentDistance: Double,
) {
    val currentMatches: Boolean = currentDistance <= CURRENT_GUESS_CONFIRM_RADIUS
}

internal fun MutableMap<Long, ArrowCandidateSequence>.currentGuessForSequence(
    targetId: Long,
    sequence: ArrowCandidateSequence,
): DianaBurrowTarget? {
    val currentGuess = DianaBurrowTargetTracker.targetAt(sequence.current.location)
    if (currentGuess == null || currentGuess.targetId != targetId || currentGuess.source != DianaBurrowSource.GUESS) {
        remove(targetId)
        return null
    }
    return currentGuess
}

internal fun ArrowCandidateSequence.matchDetectedBurrow(
    detected: WorldVec,
    currentGuess: DianaBurrowTarget,
): ArrowSequenceMatch? {
    val match = candidates
        .withIndex()
        .filter { (_, candidate) -> candidate.location.distance(detected) <= CURRENT_GUESS_CONFIRM_RADIUS }
        .minWithOrNull(
            compareBy<IndexedValue<ResolvedArrowCandidate>> { (_, candidate) -> candidate.location.distance(detected) }
                .thenBy { (index, _) -> index },
        )
        ?: return null
    return ArrowSequenceMatch(
        targetId = targetId,
        sequence = this,
        currentGuess = currentGuess,
        matchIndex = match.index,
        matchCandidate = match.value,
        matchDistance = match.value.location.distance(detected),
        currentDistance = currentGuess.location.distance(detected),
    )
}

internal fun List<ArrowSequenceMatch>.bestConfirmMatch(): ArrowSequenceMatch? =
    sortedWith(
        compareBy<ArrowSequenceMatch> { match -> !match.currentMatches }
            .thenBy { match -> match.currentDistance }
            .thenBy { match -> match.matchDistance }
            .thenBy { match -> match.matchIndex }
            .thenByDescending { match -> match.sequence.currentTrackedAtMillis }
            .thenByDescending { match -> match.sequence.sequenceId },
    ).firstOrNull()

internal fun MutableMap<Long, ArrowCandidateSequence>.confirmMatch(
    match: ArrowSequenceMatch,
    detected: WorldVec,
    now: Long,
) {
    remove(match.targetId)
    if (match.currentGuess.location != detected) {
        DianaBurrowTargetTracker.removeIfCurrent(
            target = match.currentGuess,
            now = now,
            suppress = false,
        )
    }
}

internal fun MutableMap<Long, ArrowCandidateSequence>.invalidateAmbiguousNonWinners(
    bestMatch: ArrowSequenceMatch,
    matches: List<ArrowSequenceMatch>,
) {
    matches
        .filter { match -> match.targetId != bestMatch.targetId }
        .forEach { match ->
            this[match.targetId] = match.sequence.copy(
                invalidatedBlockKeys = match.sequence.invalidatedBlockKeys + match.matchCandidate.location.blockKey(),
                missingParticlesFirstCheckAtMillis = null,
            )
        }
}

internal const val CURRENT_GUESS_CONFIRM_RADIUS = 8.0
