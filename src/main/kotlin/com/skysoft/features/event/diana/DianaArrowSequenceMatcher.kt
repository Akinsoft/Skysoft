package com.skysoft.features.event.diana

import com.skysoft.utils.WorldVec

internal data class ArrowCandidateSequence(
    val sequenceId: Long,
    val targetId: Long,
    val candidates: List<ResolvedArrowCandidate>,
    val current: ResolvedArrowCandidate,
    val currentIndex: Int,
    val currentTrackedAtMillis: Long,
    val invalidatedBlockKeys: Set<String> = emptySet(),
    val missingParticlesFirstCheckAtMillis: Long? = null,
)

internal data class ArrowSequenceMatch(
    val sequence: ArrowCandidateSequence,
    val currentGuess: DianaBurrowTarget,
    val matchIndex: Int,
    val matchCandidate: ResolvedArrowCandidate,
)

internal fun MutableMap<Long, ArrowCandidateSequence>.currentGuessForSequence(
    sequence: ArrowCandidateSequence,
): DianaBurrowTarget? {
    val currentGuess = DianaBurrowTargetTracker.targetAt(sequence.current.location)
    if (currentGuess == null || currentGuess.targetId != sequence.targetId || currentGuess.source != DianaBurrowSource.GUESS) {
        remove(sequence.targetId)
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
    remove(match.sequence.targetId)
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
        .filter { match -> match.sequence.targetId != bestMatch.sequence.targetId }
        .forEach { match ->
            val updated = match.sequence.copy(
                invalidatedBlockKeys = match.sequence.invalidatedBlockKeys + match.matchCandidate.location.blockKey(),
                missingParticlesFirstCheckAtMillis = null,
            )
            this[match.sequence.targetId] = updated
            DianaBurrowTargetTracker.updateGuessCandidates(match.currentGuess, updated.remainingCandidates(), now)
        }
}

internal fun ArrowCandidateSequence.remainingCandidates(): List<WorldVec> =
    candidates.drop(currentIndex)
        .filter { candidate -> candidate.location.blockKey() !in invalidatedBlockKeys }
        .distinctBy { candidate -> candidate.location.blockKey() }
        .map { candidate -> candidate.location }
