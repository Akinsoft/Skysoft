package com.skysoft.features.event.diana

import com.skysoft.events.particle.ClientParticleEvent
import com.skysoft.utils.WorldVec
import com.skysoft.utils.toWorldVec
import net.minecraft.client.Minecraft

internal object DianaArrowGuess {
    private val detectors = mutableMapOf<DianaArrowDistance, DianaArrowShapeDetector>()
    private val activeSequences = mutableMapOf<Long, ArrowCandidateSequence>()
    private var pendingSession: PendingArrowSession? = null
    private var spadeHeldSinceMillis = Long.MIN_VALUE
    private var nextSequenceId = 0L

    fun markBurrowRelatedMessage(
        anchor: WorldVec?,
        now: Long = System.currentTimeMillis(),
        progress: DianaBurrowProgress? = null,
        clearCurrentRadius: Double = CURRENT_GUESS_CLEAR_RADIUS,
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

    fun handleParticle(event: ClientParticleEvent, now: Long = System.currentTimeMillis()) {
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

    fun prune(now: Long = System.currentTimeMillis()) {
        flushPendingSession(now)
        detectors.values.forEach { detector -> detector.prune(now) }
        handleLoadedInvalidSequences(now)
        handleMissingBurrowParticles(
            playerLocation = Minecraft.getInstance().player?.position()?.toWorldVec(),
            particlesShouldBeVisible = burrowParticlesShouldBeVisible(now),
            now = now,
        )
    }

    fun onDetectedBurrow(location: WorldVec, now: Long = System.currentTimeMillis()): DianaBurrowTarget? {
        val detected = location.roundToBlock()
        val matches = activeSequences.entries
            .toList()
            .mapNotNull { (targetId, sequence) ->
                val currentGuess = activeSequences.currentGuessForSequence(targetId, sequence) ?: return@mapNotNull null
                sequence.matchDetectedBurrow(detected, currentGuess)
            }
        val bestMatch = matches.bestConfirmMatch() ?: return null
        if (matches.size > 1) {
            activeSequences.invalidateAmbiguousNonWinners(bestMatch, matches, now)
        }
        activeSequences.confirmMatch(bestMatch, now)
        return bestMatch.currentGuess
    }

    fun recentGuessForReplacement(
        location: WorldVec,
        now: Long = System.currentTimeMillis(),
    ): DianaBurrowTarget? {
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
        now: Long = System.currentTimeMillis(),
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

    fun handleRejectedGuess(
        target: DianaBurrowTarget,
        now: Long = System.currentTimeMillis(),
        skipCandidatesNearRejectedRadius: Double = 0.0,
    ): ArrowGuessActionResult {
        if (target.source != DianaBurrowSource.GUESS) return ArrowGuessActionResult.IGNORED
        val sequence = activeSequences[target.targetId] ?: return ArrowGuessActionResult.IGNORED
        if (sequence.current.location != target.location) return ArrowGuessActionResult.IGNORED
        val current = DianaBurrowTargetTracker.targetAt(target.location) ?: return ArrowGuessActionResult.IGNORED
        if (current.targetId != target.targetId) return ArrowGuessActionResult.IGNORED

        DianaBurrowTargetTracker.removeIfCurrent(target, now)
        val nextCandidates = sequence.candidates
            .withIndex()
            .drop(sequence.currentIndex + 1)
            .filter { (_, candidate) ->
                candidate.location.blockKey() !in sequence.invalidatedBlockKeys &&
                    (
                        skipCandidatesNearRejectedRadius <= 0.0 ||
                            candidate.location.distance(target.location) > skipCandidatesNearRejectedRadius
                        )
            }
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
            return ArrowGuessActionResult.HANDLED
        }
        activeSequences.remove(target.targetId)
        return ArrowGuessActionResult.HANDLED
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
                    invalidatedBlockKeys = emptySet(),
                    missingParticlesFirstCheckAtMillis = null,
                )
            }
    }

    fun clearDetection() {
        detectors.clear()
        pendingSession = null
        spadeHeldSinceMillis = Long.MIN_VALUE
        nextSequenceId = 0L
    }

    internal fun handleMissingBurrowParticles(
        playerLocation: WorldVec?,
        particlesShouldBeVisible: Boolean,
        now: Long = System.currentTimeMillis(),
        hasRecentBurrowAt: (WorldVec) -> Boolean = { location ->
            DianaBurrowParticleDetector.hasRecentBurrowAt(location, now)
        },
    ): ArrowGuessActionResult {
        if (!particlesShouldBeVisible || playerLocation == null) return ArrowGuessActionResult.IGNORED
        var result = ArrowGuessActionResult.IGNORED
        activeSequences.values.toList().forEach { sequence ->
            val target = activeSequences.currentGuessForSequence(sequence.targetId, sequence) ?: return@forEach
            val rejectionCandidates = sequence.candidates
                .withIndex()
                .drop(sequence.currentIndex)
                .filter { (_, candidate) -> candidate.location.blockKey() !in sequence.invalidatedBlockKeys }
                .filter { (_, candidate) ->
                    candidate.location.blockCenter().distance(playerLocation) <= MISSING_BURROW_PARTICLES_RADIUS
                }
                .filter { (_, candidate) -> !hasRecentBurrowAt(candidate.location) }
            if (rejectionCandidates.isEmpty()) {
                activeSequences[sequence.targetId] = sequence.copy(missingParticlesFirstCheckAtMillis = null)
                return@forEach
            }
            val firstCheckAtMillis = sequence.missingParticlesFirstCheckAtMillis ?: now
            if (sequence.missingParticlesFirstCheckAtMillis == null) {
                activeSequences[sequence.targetId] = sequence.copy(missingParticlesFirstCheckAtMillis = firstCheckAtMillis)
                return@forEach
            }
            if (now - firstCheckAtMillis < MISSING_BURROW_PARTICLES_SECOND_CHECK_MILLIS) return@forEach
            val rejection = rejectCandidateRegion(
                target = target,
                sequence = sequence,
                rejectedCandidates = rejectionCandidates,
                now = now,
            )
            if (rejection == ArrowGuessActionResult.HANDLED) result = ArrowGuessActionResult.HANDLED
        }
        return result
    }

    internal fun trackResolvedCandidates(
        candidates: List<ResolvedArrowCandidate>,
        now: Long,
        progress: DianaBurrowProgress? = null,
    ): DianaBurrowTarget? {
        pruneExpiredSequences()
        for ((index, candidate) in candidates.withIndex()) {
            val target = DianaBurrowTargetTracker.trackGuess(
                location = candidate.location,
                now = now,
                candidates = candidates.drop(index).map { remaining -> remaining.location },
            ) ?: continue
            if (target.source == DianaBurrowSource.GUESS) {
                val sequenceId = ++nextSequenceId
                activeSequences[target.targetId] = ArrowCandidateSequence(
                    sequenceId = sequenceId,
                    targetId = target.targetId,
                    candidates = candidates,
                    current = candidate,
                    currentIndex = index,
                    currentTrackedAtMillis = now,
                    invalidatedBlockKeys = emptySet(),
                    missingParticlesFirstCheckAtMillis = null,
                )
            }
            DianaBurrowChainState.onNextTargetAssigned(target, progress, now)
            return target
        }
        return null
    }

    private fun rejectCandidateRegion(
        target: DianaBurrowTarget,
        sequence: ArrowCandidateSequence,
        rejectedCandidates: List<IndexedValue<ResolvedArrowCandidate>>,
        now: Long,
    ): ArrowGuessActionResult {
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
            return ArrowGuessActionResult.HANDLED
        }
        DianaBurrowInteractions.hasPendingClick(target, clear = true)
        DianaBurrowTargetTracker.removeIfCurrent(target, now, suppress = false)
        val nextCandidates = sequence.candidates
            .withIndex()
            .drop(sequence.currentIndex + 1)
            .filter { (_, candidate) -> candidate.location.blockKey() !in invalidatedKeys }
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
                invalidatedBlockKeys = invalidatedKeys,
                missingParticlesFirstCheckAtMillis = null,
            )
            return ArrowGuessActionResult.HANDLED
        }
        activeSequences.remove(target.targetId)
        return ArrowGuessActionResult.HANDLED
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

    internal fun handleLoadedInvalidSequences(
        now: Long,
        checkSurface: (WorldVec) -> DianaBurrowSurfaceStatus = DianaBurrowSurfaceValidator::check,
    ): ArrowGuessActionResult {
        var result = ArrowGuessActionResult.IGNORED
        var advanced: Boolean
        do {
            advanced = false
            activeSequences.values.toList().forEach { sequence ->
                val target = DianaBurrowTargetTracker.targetAt(sequence.current.location) ?: return@forEach
                if (target.targetId != sequence.targetId || target.source != DianaBurrowSource.GUESS) return@forEach
                if (DianaNonSpadeGuessBreaks.hasRecentBreakAttempt(target, now)) return@forEach
                if (DianaBurrowInteractions.hasPendingClick(target)) return@forEach
                if (checkSurface(target.location) != DianaBurrowSurfaceStatus.INVALID) return@forEach
                val rejection = handleRejectedGuess(target, now)
                if (rejection == ArrowGuessActionResult.HANDLED) {
                    advanced = true
                    result = ArrowGuessActionResult.HANDLED
                }
            }
        } while (advanced)
        return result
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

internal fun DianaArrowRay.isFromAnchor(anchor: WorldVec): Boolean =
    origin.roundToBlock() == anchor.roundToBlock()

internal enum class ArrowGuessActionResult {
    HANDLED,
    IGNORED,
}
