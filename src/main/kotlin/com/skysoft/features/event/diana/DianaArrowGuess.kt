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
        val pending = pendingRay(ray, session.anchor, ray.resolveCandidates(HUB_BOUNDS)) ?: return
        pendingSession = null
        trackPendingRay(pending, session.anchor, now, session.progress)
    }

    private fun pendingRay(
        ray: DianaArrowRay,
        anchor: WorldVec,
        candidates: List<ResolvedArrowCandidate>,
    ): PendingArrowRay? {
        if (!ray.isFromAnchor(anchor) || candidates.isEmpty()) return null
        return PendingArrowRay(ray, candidates)
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
            activeSequences.invalidateAmbiguousNonWinners(bestMatch, matches)
        }
        activeSequences.confirmMatch(bestMatch, detected, now)
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
        for ((index, candidate) in nextCandidates) {
            val next = DianaBurrowTargetTracker.trackGuess(candidate.location, now) ?: continue
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
        hasRecentBurrowNear: (WorldVec) -> Boolean = { location ->
            DianaBurrowParticleDetector.hasRecentBurrowNear(location, CURRENT_GUESS_CONFIRM_RADIUS, now)
        },
    ): ArrowGuessActionResult {
        if (!particlesShouldBeVisible || playerLocation == null) return ArrowGuessActionResult.IGNORED
        var result = ArrowGuessActionResult.IGNORED
        activeSequences.values.toList().forEach { sequence ->
            val target = activeSequences.currentGuessForSequence(sequence.targetId, sequence) ?: return@forEach
            if (DianaBurrowInteractions.hasPendingClick(target)) return@forEach
            val rejectionCandidates = sequence.candidates
                .withIndex()
                .drop(sequence.currentIndex)
                .filter { (_, candidate) -> candidate.location.blockKey() !in sequence.invalidatedBlockKeys }
                .filter { (_, candidate) ->
                    candidate.location.blockCenter().distance(playerLocation) <= MISSING_BURROW_PARTICLES_RADIUS
                }
                .filter { (_, candidate) -> !hasRecentBurrowNear(candidate.location) }
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
        distanceHint: DianaArrowDistance? = null,
        progress: DianaBurrowProgress? = null,
    ): DianaBurrowTarget? {
        pruneExpiredSequences()
        val orderedCandidates = DianaArrowCandidateResolver.rank(candidates, distanceHint)
        if (orderedCandidates.isEmpty()) return null
        for ((index, candidate) in orderedCandidates.withIndex()) {
            val target = DianaBurrowTargetTracker.trackGuess(candidate.location, now) ?: continue
            if (target.source == DianaBurrowSource.GUESS) {
                val sequenceId = ++nextSequenceId
                activeSequences[target.targetId] = ArrowCandidateSequence(
                    sequenceId = sequenceId,
                    targetId = target.targetId,
                    candidates = orderedCandidates,
                    current = candidate,
                    currentIndex = index,
                    distanceHint = distanceHint,
                    createdAtMillis = now,
                    currentTrackedAtMillis = now,
                    firstGuess = target.location,
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
            activeSequences[sequence.targetId] = sequence.copy(
                invalidatedBlockKeys = invalidatedKeys,
                missingParticlesFirstCheckAtMillis = null,
            )
            return ArrowGuessActionResult.HANDLED
        }
        DianaBurrowInteractions.hasPendingClick(target, clear = true)
        DianaBurrowTargetTracker.removeIfCurrent(target, now, suppress = false)
        val nextCandidates = sequence.candidates
            .withIndex()
            .drop(sequence.currentIndex + 1)
            .filter { (_, candidate) -> candidate.location.blockKey() !in invalidatedKeys }
        for ((index, candidate) in nextCandidates) {
            val next = DianaBurrowTargetTracker.trackGuess(candidate.location, now) ?: continue
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

    private fun trackPendingRay(
        pending: PendingArrowRay,
        anchor: WorldVec,
        now: Long,
        progress: DianaBurrowProgress?,
    ): DianaBurrowTarget? {
        if (!pending.ray.isFromAnchor(anchor)) return null
        return trackResolvedCandidates(pending.candidates, now, pending.ray.distanceHint, progress)
    }

    private fun pruneExpiredSequences() {
        activeSequences.entries.removeIf { (_, sequence) ->
            DianaBurrowTargetTracker.targetAt(sequence.current.location)?.targetId != sequence.targetId
        }
    }

    internal fun handleLoadedInvalidSequences(
        now: Long,
        checkSurface: (WorldVec) -> DianaBurrowSurfaceCheck = DianaBurrowSurfaceValidator::check,
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
                val surface = checkSurface(target.location)
                if (surface.status != DianaBurrowSurfaceStatus.INVALID) return@forEach
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

    internal data class PendingArrowRay(
        val ray: DianaArrowRay,
        val candidates: List<ResolvedArrowCandidate>,
    )

    private const val ARROW_COLLECTION_WINDOW_MILLIS = 3_000L
    private const val CROSS_SIGNAL_REPLACEMENT_MILLIS = 10_000L
    private const val BURROW_PARTICLE_VISIBILITY_MILLIS = 1_000L
    private const val MISSING_BURROW_PARTICLES_SECOND_CHECK_MILLIS = 500L
    private const val MISSING_BURROW_PARTICLES_RADIUS = 22.0
    private const val CURRENT_GUESS_CLEAR_RADIUS = 50.0
    private val HUB_BOUNDS = DianaArrowBounds(
        min = WorldVec(-283.0, 0.0, -208.0),
        max = WorldVec(175.0, 256.0, 205.0),
    )
}

internal fun DianaArrowRay.isFromAnchor(anchor: WorldVec): Boolean =
    origin.roundToBlock() == anchor.roundToBlock()

internal enum class ArrowGuessActionResult {
    HANDLED,
    IGNORED,
}
