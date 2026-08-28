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
)

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
        .firstOrNull { (_, candidate) -> candidate.location == detected }
        ?: return null
    return ArrowSequenceMatch(
        targetId = targetId,
        sequence = this,
        currentGuess = currentGuess,
        matchIndex = match.index,
        matchCandidate = match.value,
    )
}

internal fun List<ArrowSequenceMatch>.bestConfirmMatch(): ArrowSequenceMatch? =
    sortedWith(
        compareBy<ArrowSequenceMatch> { match -> match.currentGuess.location != match.matchCandidate.location }
            .thenBy { match -> match.matchIndex }
            .thenByDescending { match -> match.sequence.currentTrackedAtMillis }
            .thenByDescending { match -> match.sequence.sequenceId },
    ).firstOrNull()

internal fun MutableMap<Long, ArrowCandidateSequence>.confirmMatch(
    match: ArrowSequenceMatch,
    now: Long,
) {
    remove(match.targetId)
    if (match.currentGuess.location != match.matchCandidate.location) {
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
    now: Long,
) {
    matches
        .filter { match -> match.targetId != bestMatch.targetId }
        .forEach { match ->
            val updated = match.sequence.copy(
                invalidatedBlockKeys = match.sequence.invalidatedBlockKeys + match.matchCandidate.location.blockKey(),
                missingParticlesFirstCheckAtMillis = null,
            )
            this[match.targetId] = updated
            DianaBurrowTargetTracker.updateGuessCandidates(match.currentGuess, updated.remainingCandidates(), now)
        }
}

internal fun ArrowCandidateSequence.remainingCandidates(): List<WorldVec> =
    candidates.drop(currentIndex)
        .filter { candidate -> candidate.location.blockKey() !in invalidatedBlockKeys }
        .distinctBy { candidate -> candidate.location.blockKey() }
        .map { candidate -> candidate.location }
