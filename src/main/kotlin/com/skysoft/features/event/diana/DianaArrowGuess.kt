package com.skysoft.features.event.diana

import com.skysoft.events.particle.ClientParticleEvent
import com.skysoft.utils.WorldVec
import com.skysoft.utils.toWorldVec
import net.minecraft.client.Minecraft
import kotlin.math.roundToInt

internal object DianaArrowGuess {
    private val detector = DianaArrowShapeDetector()
    private val recentRayKeys = mutableMapOf<String, Long>()
    private val recentRays = mutableListOf<RecentArrowRay>()
    private val activeSequences = mutableMapOf<Long, ArrowCandidateSequence>()
    private var pendingSession: PendingArrowSession? = null
    private var lastBurrowRelatedMessageMillis = Long.MIN_VALUE
    private var spadeHeldSinceMillis = Long.MIN_VALUE
    private var nextSequenceId = 0L

    fun markBurrowRelatedMessage(
        anchor: WorldVec?,
        now: Long = System.currentTimeMillis(),
        playerLocation: WorldVec? = null,
        progress: DianaBurrowProgress? = null,
        clearCurrentReason: String? = null,
        clearCurrentRadius: Double = CURRENT_GUESS_CLEAR_RADIUS,
    ) {
        if (clearCurrentReason != null) {
            pendingSession = null
            clearCurrentGuess(anchor?.blockCenter() ?: playerLocation, now, clearCurrentRadius)
        }
        flushPendingSession(now)
        lastBurrowRelatedMessageMillis = now
        val anchorBlock = anchor?.roundToBlock()
        val session = pendingSession
        val canExtendSession = session != null && now <= session.expiresAtMillis
        pendingSession = if (canExtendSession) {
            session.copy(
                expiresAtMillis = now + ARROW_COLLECTION_WINDOW_MILLIS,
                anchor = session.anchor ?: anchorBlock,
                progress = progress ?: session.progress,
            )
        } else {
            PendingArrowSession(
                expiresAtMillis = now + ARROW_COLLECTION_WINDOW_MILLIS,
                anchor = anchorBlock,
                progress = progress,
            )
        }
        val activeSession = pendingSession ?: return
        recentRays.removeIf { ray -> now - ray.seenAtMillis > RECENT_ARROW_RAY_MILLIS }
        val recent = recentRays
            .asSequence()
            .filter { ray -> ray.pending.ray.isFromAnchor(activeSession.anchor) }
            .filter { ray -> recentRayKeys[ray.pending.key]?.let { it > now } != true }
            .filter { ray -> !activeSession.hasRay(ray.pending.key) }
            .minWithOrNull(compareBy<RecentArrowRay> { it.playerDistanceSq }.thenByDescending { it.seenAtMillis })
            ?: return
        activeSession.rays += recent.pending
        recentRays.remove(recent)
        flushPendingSession(now, resolvingRayKey = recent.pending.key)
    }

    fun handleParticle(event: ClientParticleEvent, now: Long = System.currentTimeMillis()) {
        val distanceHint = DianaParticleClassifier.arrowDistance(event) ?: return
        val playerLocation = Minecraft.getInstance().player?.position()?.toWorldVec() ?: return
        val distanceFromPlayer = event.location.distance(playerLocation)
        if (distanceFromPlayer > MAX_ARROW_PARTICLE_DISTANCE) return
        val ray = detector.add(event.location, distanceHint, now) ?: return
        val rayKey = ray.dedupeKey()
        val candidates = ray.resolveCandidates(HUB_BOUNDS).takeIf { it.isNotEmpty() }
        prune(now)
        if (candidates != null) {
            recentRays += RecentArrowRay(
                pending = PendingArrowRay(ray, rayKey, candidates),
                seenAtMillis = now,
                playerDistanceSq = event.location.distanceSq(playerLocation),
            )
        }
        val session = pendingSession
        if (
            session != null &&
            candidates != null &&
            canAttachRay(ray, session, now) &&
            recentRayKeys[rayKey]?.let { it > now } != true &&
            !session.hasRay(rayKey)
        ) {
            session.rays += PendingArrowRay(ray, rayKey, candidates)
            flushPendingSession(now, resolvingRayKey = rayKey)
        }
    }

    private fun canAttachRay(ray: DianaArrowRay, session: PendingArrowSession, now: Long): Boolean =
        lastBurrowRelatedMessageMillis != Long.MIN_VALUE &&
            now - lastBurrowRelatedMessageMillis in 0..BURROW_MESSAGE_WINDOW_MILLIS &&
            ray.isFromAnchor(session.anchor)

    fun prune(now: Long = System.currentTimeMillis()) {
        flushPendingSession(now)
        detector.prune(now)
        recentRayKeys.entries.removeIf { (_, expiresAt) -> expiresAt <= now }
        recentRays.removeIf { ray -> now - ray.seenAtMillis > RECENT_ARROW_RAY_MILLIS }
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
        detector.clear()
        recentRayKeys.clear()
        recentRays.clear()
        pendingSession = null
        lastBurrowRelatedMessageMillis = Long.MIN_VALUE
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

    private fun flushPendingSession(now: Long, resolvingRayKey: String? = null) {
        val session = pendingSession ?: return
        if (now <= session.expiresAtMillis && resolvingRayKey == null) return
        pendingSession = null
        val clusters = session.rays.clusterArrowRays()
        val trackedClusters = resolvingRayKey?.let { rayKey ->
            clusters.filter { cluster -> cluster.any { ray -> ray.key == rayKey } }
        } ?: clusters
        val progress = session.progress.takeIf { clusters.size == 1 }
        trackedClusters.forEach { cluster -> trackPendingCluster(cluster, now, progress) }
    }

    private fun trackPendingCluster(
        cluster: List<PendingArrowRay>,
        now: Long,
        progress: DianaBurrowProgress?,
    ): DianaBurrowTarget? {
        val distanceHint = cluster.map { it.ray.distanceHint }.distinct().singleOrNull()
        val candidates = cluster.flatMap { it.candidates }
        val target = trackResolvedCandidates(candidates, now, distanceHint, progress) ?: return null
        cluster.forEach { ray -> recentRayKeys[ray.key] = now + RAY_DEDUPE_MILLIS }
        return target
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
        val anchor: WorldVec?,
        val progress: DianaBurrowProgress?,
        val rays: MutableList<PendingArrowRay> = mutableListOf(),
    ) {
        fun hasRay(key: String): Boolean =
            rays.any { ray -> ray.key == key }
    }

    internal data class PendingArrowRay(
        val ray: DianaArrowRay,
        val key: String,
        val candidates: List<ResolvedArrowCandidate>,
    )

    private const val MAX_ARROW_PARTICLE_DISTANCE = 6.0
    private const val ARROW_COLLECTION_WINDOW_MILLIS = 3_000L
    private const val BURROW_MESSAGE_WINDOW_MILLIS = ARROW_COLLECTION_WINDOW_MILLIS
    private const val RECENT_ARROW_RAY_MILLIS = 1_500L
    private const val CROSS_SIGNAL_REPLACEMENT_MILLIS = 10_000L
    private const val RAY_DEDUPE_MILLIS = 18_000L
    private const val BURROW_PARTICLE_VISIBILITY_MILLIS = 1_000L
    private const val MISSING_BURROW_PARTICLES_SECOND_CHECK_MILLIS = 500L
    private const val MISSING_BURROW_PARTICLES_RADIUS = 22.0
    private const val CURRENT_GUESS_CLEAR_RADIUS = 50.0
    private val HUB_BOUNDS = DianaArrowBounds(
        min = WorldVec(-283.0, 0.0, -208.0),
        max = WorldVec(175.0, 256.0, 205.0),
    )

    private data class RecentArrowRay(
        val pending: PendingArrowRay,
        val seenAtMillis: Long,
        val playerDistanceSq: Double,
    )
}

private const val ARROW_ANCHOR_HORIZONTAL_RADIUS = 8.0
private const val RAY_KEY_SCALE = 100.0

internal fun DianaArrowRay.isFromAnchor(anchor: WorldVec?): Boolean {
    anchor ?: return true
    return origin.horizontalDistanceSq(anchor.blockCenter()) <= ARROW_ANCHOR_HORIZONTAL_RADIUS * ARROW_ANCHOR_HORIZONTAL_RADIUS
}

private fun DianaArrowRay.dedupeKey(): String {
    val origin = origin.roundToBlock()
    return buildString {
        append(origin.blockKey())
        append(':')
        append(direction.x.roundedKey())
        append(':')
        append(direction.y.roundedKey())
        append(':')
        append(direction.z.roundedKey())
        append(':')
        append(distanceHint.name)
    }
}

private fun Double.roundedKey(): Int = (this * RAY_KEY_SCALE).roundToInt()

private fun WorldVec.horizontalDistanceSq(other: WorldVec): Double {
    val dx = x - other.x
    val dz = z - other.z
    return dx * dx + dz * dz
}

internal fun List<DianaArrowGuess.PendingArrowRay>.clusterArrowRays(): List<List<DianaArrowGuess.PendingArrowRay>> {
    val clusters = mutableListOf<MutableList<DianaArrowGuess.PendingArrowRay>>()
    for (ray in this) {
        val cluster = clusters.firstOrNull { existing -> ray.isSameArrowCluster(existing.first()) }
        if (cluster != null) {
            cluster += ray
        } else {
            clusters += mutableListOf(ray)
        }
    }
    return clusters
}

private fun DianaArrowGuess.PendingArrowRay.isSameArrowCluster(other: DianaArrowGuess.PendingArrowRay): Boolean {
    if (ray.distanceHint != other.ray.distanceHint) return false
    if (ray.direction.dot(other.ray.direction) < ARROW_CLUSTER_MIN_DIRECTION_DOT) return false
    if (ray.origin.distance(other.ray.origin) > ARROW_CLUSTER_MAX_ORIGIN_DISTANCE) return false
    if (ray.origin.distanceToArrowLine(other.ray) > ARROW_CLUSTER_MAX_PERPENDICULAR_DISTANCE) return false
    if (other.ray.origin.distanceToArrowLine(ray) > ARROW_CLUSTER_MAX_PERPENDICULAR_DISTANCE) return false
    return true
}

private fun WorldVec.distanceToArrowLine(ray: DianaArrowRay): Double {
    val fromOrigin = this - ray.origin
    val projection = fromOrigin.dot(ray.direction)
    val closestPoint = ray.origin + ray.direction * projection
    return distance(closestPoint)
}

internal enum class ArrowGuessActionResult {
    HANDLED,
    IGNORED,
}

private const val ARROW_CLUSTER_MIN_DIRECTION_DOT = 0.995
private const val ARROW_CLUSTER_MAX_ORIGIN_DISTANCE = 6.0
private const val ARROW_CLUSTER_MAX_PERPENDICULAR_DISTANCE = 1.5
