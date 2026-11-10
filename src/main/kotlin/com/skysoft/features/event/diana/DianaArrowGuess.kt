package com.skysoft.features.event.diana

import com.skysoft.events.particle.ClientParticleEvent
import com.skysoft.utils.PlayerUtilities.localPlayerLocation
import com.skysoft.utils.WorldVec

internal object DianaArrowGuess {
    private val detectors = mutableMapOf<DianaArrowDistance, DianaArrowShapeDetector>()
    private val activeSequences = mutableMapOf<Long, ArrowCandidateSequence>()
    private var pendingSession: PendingArrowSession? = null
    private var spadeHeldSinceMillis = Long.MIN_VALUE
    private var nextSequenceId = 0L

    fun markBurrowRelatedMessage(
        anchor: WorldVec?,
        now: Long,
        progress: DianaBurrowProgress?,
        clearCurrentRadius: Double,
    ) {
        pendingSession = null
        detectors.clear()
        clearCurrentGuess(anchor?.blockCenter(), now, clearCurrentRadius)
        val anchorBlock = anchor?.roundToBlock() ?: return
        pendingSession = PendingArrowSession(
            expiresAtMillis = now + ARROW_COLLECTION_WINDOW_MILLIS,
            anchor = anchorBlock,
            progress = progress,
        )
    }

    fun handleParticle(event: ClientParticleEvent, now: Long) {
        flushPendingSession(now)
        val session = pendingSession ?: return
        val distanceHint = DianaParticleClassifier.arrowDistance(event) ?: return
        val ray = detectors.getOrPut(distanceHint) { DianaArrowShapeDetector() }
            .add(event.location, distanceHint, now) ?: return
        if (!ray.isFromAnchor(session.anchor)) return
        val candidates = DianaArrowCandidateResolver.resolve(ray, dianaHubBounds)
        if (candidates.isEmpty()) return
        pendingSession = null
        trackResolvedCandidates(candidates, now, session.progress)
    }

    fun prune(now: Long) {
        flushPendingSession(now)
        detectors.values.forEach { detector -> detector.prune(now) }
        handleLoadedInvalidSequences(now)
        if (burrowParticlesShouldBeVisible(now)) {
            localPlayerLocation()?.let { playerLocation -> handleMissingBurrowParticles(playerLocation, now) }
        }
    }

    fun onDetectedBurrow(location: WorldVec, now: Long): DianaBurrowTarget? {
        val detected = location.roundToBlock()
        val matches = activeSequences.values
            .toList()
            .mapNotNull { sequence ->
                val currentGuess = activeSequences.currentGuessForSequence(sequence) ?: return@mapNotNull null
                sequence.matchDetectedBurrow(detected, currentGuess)
            }
        val bestMatch = matches.bestConfirmMatch() ?: return null
        if (matches.size > 1) {
            activeSequences.invalidateAmbiguousNonWinners(bestMatch, matches, now)
        }
        activeSequences.confirmMatch(bestMatch, now)
        return bestMatch.currentGuess
    }

    fun recentGuessForReplacement(location: WorldVec, now: Long): DianaBurrowTarget? {
        pruneExpiredSequences()
        val block = location.roundToBlock()
        return activeSequences.values
            .filter { sequence -> now - sequence.currentTrackedAtMillis <= CROSS_SIGNAL_REPLACEMENT_MILLIS }
            .mapNotNull { sequence -> DianaBurrowTargetTracker.targetAt(sequence.current.location) }
            .filter { target -> target.source == DianaBurrowSource.GUESS }
            .minByOrNull { target -> target.location.distanceSq(block) }
    }

    fun clearActiveGuess(target: DianaBurrowTarget) {
        activeSequences.remove(target.targetId)
    }

    fun clearCurrentGuess(
        playerLocation: WorldVec?,
        now: Long,
        maxDistance: Double = CURRENT_GUESS_CLEAR_RADIUS,
    ): DianaBurrowTarget? {
        playerLocation ?: return null
        pruneExpiredSequences()
        val target = DianaBurrowTargetTracker.snapshot()
            .asSequence()
            .filter { candidate -> candidate.source == DianaBurrowSource.GUESS }
            .minByOrNull { candidate -> candidate.location.blockCenter().distanceSq(playerLocation) }
            ?: return null
        if (target.location.blockCenter().distance(playerLocation) > maxDistance) return null
        activeSequences.remove(target.targetId)
        return DianaBurrowTargetTracker.removeIfCurrent(target, now, suppress = false)
    }

    fun handleRejectedGuess(target: DianaBurrowTarget, now: Long): Boolean {
        if (target.source != DianaBurrowSource.GUESS) return false
        val sequence = activeSequences[target.targetId] ?: return false
        if (sequence.current.location != target.location) return false
        val current = DianaBurrowTargetTracker.targetAt(target.location) ?: return false
        if (current.targetId != target.targetId) return false

        DianaBurrowTargetTracker.removeIfCurrent(target, now)
        advanceSequence(target, sequence, now)
        return true
    }

    fun clear() {
        clearDetection()
        activeSequences.clear()
    }

    fun restoreSequences(targets: List<DianaBurrowTarget>, now: Long) {
        activeSequences.clear()
        nextSequenceId = 0L
        targets
            .filter { target -> target.source == DianaBurrowSource.GUESS }
            .forEach { target ->
                val candidates = target.guessCandidates
                    .ifEmpty { listOf(target.location) }
                    .map { location -> location.toRestoredCandidate() }
                activeSequences[target.targetId] = ArrowCandidateSequence(
                    sequenceId = ++nextSequenceId,
                    targetId = target.targetId,
                    candidates = candidates,
                    current = candidates.first(),
                    currentIndex = 0,
                    currentTrackedAtMillis = now,
                )
            }
    }

    private fun clearDetection() {
        detectors.clear()
        pendingSession = null
        spadeHeldSinceMillis = Long.MIN_VALUE
        nextSequenceId = 0L
    }

    private fun handleMissingBurrowParticles(playerLocation: WorldVec, now: Long) {
        activeSequences.values.toList().forEach { sequence ->
            val target = activeSequences.currentGuessForSequence(sequence) ?: return@forEach
            val rejectionCandidates = sequence.candidates
                .withIndex()
                .drop(sequence.currentIndex)
                .filter { (_, candidate) -> candidate.location.blockKey() !in sequence.invalidatedBlockKeys }
                .filter { (_, candidate) ->
                    candidate.location.blockCenter().distance(playerLocation) <= MISSING_BURROW_PARTICLES_RADIUS
                }
                .filter { (_, candidate) -> !DianaBurrowParticleDetector.hasRecentBurrowAt(candidate.location, now) }
            if (rejectionCandidates.isEmpty()) {
                activeSequences[sequence.targetId] = sequence.copy(missingParticlesFirstCheckAtMillis = null)
                return@forEach
            }
            val firstCheckAtMillis = sequence.missingParticlesFirstCheckAtMillis
            if (firstCheckAtMillis == null) {
                activeSequences[sequence.targetId] = sequence.copy(missingParticlesFirstCheckAtMillis = now)
                return@forEach
            }
            if (now - firstCheckAtMillis < MISSING_BURROW_PARTICLES_SECOND_CHECK_MILLIS) return@forEach
            rejectCandidateRegion(target, sequence, rejectionCandidates, now)
        }
    }

    private fun trackResolvedCandidates(
        candidates: List<ResolvedArrowCandidate>,
        now: Long,
        progress: DianaBurrowProgress?,
    ) {
        pruneExpiredSequences()
        for ((index, candidate) in candidates.withIndex()) {
            val target = DianaBurrowTargetTracker.trackGuess(
                location = candidate.location,
                now = now,
                candidates = candidates.drop(index).map { remaining -> remaining.location },
            ) ?: continue
            if (target.source == DianaBurrowSource.GUESS) {
                activeSequences[target.targetId] = ArrowCandidateSequence(
                    sequenceId = ++nextSequenceId,
                    targetId = target.targetId,
                    candidates = candidates,
                    current = candidate,
                    currentIndex = index,
                    currentTrackedAtMillis = now,
                )
            }
            DianaBurrowChainState.onNextTargetAssigned(target, progress, now)
            return
        }
    }

    private fun rejectCandidateRegion(
        target: DianaBurrowTarget,
        sequence: ArrowCandidateSequence,
        rejectedCandidates: List<IndexedValue<ResolvedArrowCandidate>>,
        now: Long,
    ) {
        val rejectedKeys = rejectedCandidates.map { (_, candidate) -> candidate.location.blockKey() }.toSet()
        val currentRejected = target.location.blockKey() in rejectedKeys
        val invalidatedKeys = sequence.invalidatedBlockKeys + rejectedKeys
        if (!currentRejected) {
            val updated = sequence.copy(
                invalidatedBlockKeys = invalidatedKeys,
                missingParticlesFirstCheckAtMillis = null,
            )
            activeSequences[sequence.targetId] = updated
            DianaBurrowTargetTracker.updateGuessCandidates(target, updated.remainingCandidates(), now)
            return
        }
        DianaBurrowInteractions.clearPendingClicks(target)
        DianaBurrowTargetTracker.removeIfCurrent(target, now, suppress = false)
        advanceSequence(target, sequence.copy(invalidatedBlockKeys = invalidatedKeys), now)
    }

    private fun advanceSequence(target: DianaBurrowTarget, sequence: ArrowCandidateSequence, now: Long) {
        val nextCandidates = sequence.candidates
            .withIndex()
            .drop(sequence.currentIndex + 1)
            .filter { (_, candidate) -> candidate.location.blockKey() !in sequence.invalidatedBlockKeys }
        for ((remainingIndex, indexedCandidate) in nextCandidates.withIndex()) {
            val (index, candidate) = indexedCandidate
            val next = DianaBurrowTargetTracker.trackGuess(
                location = candidate.location,
                now = now,
                candidates = nextCandidates.drop(remainingIndex).map { (_, remaining) -> remaining.location },
            ) ?: continue
            DianaBurrowChainState.onTargetReplaced(target, next, now)
            activeSequences.remove(target.targetId)
            activeSequences[next.targetId] = sequence.copy(
                targetId = next.targetId,
                current = candidate,
                currentIndex = index,
                currentTrackedAtMillis = now,
                missingParticlesFirstCheckAtMillis = null,
            )
            return
        }
        activeSequences.remove(target.targetId)
    }

    private fun flushPendingSession(now: Long) {
        val session = pendingSession ?: return
        if (now < session.expiresAtMillis) return
        pendingSession = null
    }

    private fun pruneExpiredSequences() {
        activeSequences.entries.removeIf { (_, sequence) ->
            DianaBurrowTargetTracker.targetAt(sequence.current.location)?.targetId != sequence.targetId
        }
    }

    private fun handleLoadedInvalidSequences(now: Long) {
        var advanced: Boolean
        do {
            advanced = false
            activeSequences.values.toList().forEach { sequence ->
                val target = DianaBurrowTargetTracker.targetAt(sequence.current.location) ?: return@forEach
                if (target.targetId != sequence.targetId || target.source != DianaBurrowSource.GUESS) return@forEach
                if (DianaNonSpadeGuessBreaks.hasRecentBreakAttempt(target, now)) return@forEach
                if (DianaBurrowInteractions.hasPendingClick(target)) return@forEach
                if (DianaBurrowSurfaceValidator.check(target.location) != DianaBurrowSurfaceStatus.INVALID) return@forEach
                if (handleRejectedGuess(target, now)) advanced = true
            }
        } while (advanced)
    }

    private fun burrowParticlesShouldBeVisible(now: Long): Boolean {
        val isHoldingSpade = DianaEventState.isHoldingSpade()
        spadeHeldSinceMillis = when {
            isHoldingSpade && spadeHeldSinceMillis == Long.MIN_VALUE -> now
            isHoldingSpade -> spadeHeldSinceMillis
            else -> Long.MIN_VALUE
        }
        return spadeHeldSinceMillis != Long.MIN_VALUE && now - spadeHeldSinceMillis >= BURROW_PARTICLE_VISIBILITY_MILLIS
    }

    private data class PendingArrowSession(
        val expiresAtMillis: Long,
        val anchor: WorldVec,
        val progress: DianaBurrowProgress?,
    )

    private const val ARROW_COLLECTION_WINDOW_MILLIS = 3_000L
    private const val CROSS_SIGNAL_REPLACEMENT_MILLIS = 10_000L
    private const val BURROW_PARTICLE_VISIBILITY_MILLIS = 1_000L
    private const val MISSING_BURROW_PARTICLES_SECOND_CHECK_MILLIS = 500L
    private const val MISSING_BURROW_PARTICLES_RADIUS = 30.0
    private const val CURRENT_GUESS_CLEAR_RADIUS = 50.0
}

private fun WorldVec.toRestoredCandidate(): ResolvedArrowCandidate =
    ResolvedArrowCandidate(roundToBlock(), 0.0)

private fun DianaArrowRay.isFromAnchor(anchor: WorldVec): Boolean =
    origin.roundToBlock() == anchor.roundToBlock()
