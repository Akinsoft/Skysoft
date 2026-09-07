package com.skysoft.features.fishing

import com.skysoft.utils.WorldVec
import java.util.UUID

@JvmInline
internal value class FishingHotspotId(val value: Long)

internal data class FishingHotspotObservation(
    val entityUuid: UUID,
    val share: FishingHotspotShare,
)

internal data class TrackedFishingHotspot(
    val id: FishingHotspotId,
    val entityUuid: UUID,
    val share: FishingHotspotShare,
    val lastSeenAtMillis: Long,
    val isVisible: Boolean,
    val isAnnounced: Boolean,
)

internal data class FishingHotspotLifecycleChange(
    val appeared: List<TrackedFishingHotspot>,
    val closed: List<TrackedFishingHotspot>,
)

internal data class FishingHotspotPartyShareResult(
    val announcedHotspotId: FishingHotspotId?,
    val hasStoredNearbyClaim: Boolean,
)

private data class NearbyFishingHotspotPartyClaim(
    val share: FishingHotspotShare,
    val expiresAtMillis: Long,
)

internal class FishingHotspotTracker(
    private val missingGraceMillis: Long = MISSING_GRACE_MILLIS,
    private val nearbyClaimMillis: Long = NEARBY_CLAIM_MILLIS,
    private val nearbyShareRadiusSq: Double = NEARBY_SHARE_RADIUS_SQ,
) {
    private val activeHotspots = linkedMapOf<FishingHotspotId, ActiveFishingHotspot>()
    private val nearbyPartyClaims = mutableListOf<NearbyFishingHotspotPartyClaim>()
    private var nextHotspotId = 1L

    fun reconcile(
        observations: Collection<FishingHotspotObservation>,
        isLocationLoaded: (WorldVec) -> Boolean,
        now: Long,
    ): FishingHotspotLifecycleChange {
        pruneClaims(now)
        val unmatchedIds = activeHotspots.keys.toMutableSet()
        val appearedIds = mutableListOf<FishingHotspotId>()

        observations.forEach { observation ->
            val matchingId = matchingHotspotId(observation, unmatchedIds)
            if (matchingId == null) {
                val hotspot = ActiveFishingHotspot(
                    id = FishingHotspotId(nextHotspotId++),
                    entityUuid = observation.entityUuid,
                    share = observation.share,
                    lastSeenAtMillis = now,
                )
                activeHotspots[hotspot.id] = hotspot
                appearedIds += hotspot.id
            } else {
                activeHotspots.getValue(matchingId).observe(observation, now)
                unmatchedIds -= matchingId
            }
        }

        applyNearbyClaims(appearedIds)
        val closed = closeMissingHotspots(unmatchedIds, isLocationLoaded, now)
        return FishingHotspotLifecycleChange(
            appeared = appearedIds.mapNotNull { id -> activeHotspots[id]?.snapshot() },
            closed = closed,
        )
    }

    fun recordPartyShare(
        share: FishingHotspotShare,
        playerLocation: WorldVec?,
        now: Long,
    ): FishingHotspotPartyShareResult {
        pruneClaims(now)
        val exactMatch = activeHotspots.values
            .filter { hotspot -> hotspot.share.matches(share) }
            .minByOrNull { hotspot -> hotspot.share.location.distanceSq(share.location) }
        if (exactMatch != null) {
            exactMatch.isAnnounced = true
            return FishingHotspotPartyShareResult(exactMatch.id, hasStoredNearbyClaim = false)
        }

        val isPlayerNearby = playerLocation?.distanceSq(share.location)?.let { it <= nearbyShareRadiusSq } == true
        if (!isPlayerNearby) return FishingHotspotPartyShareResult(null, hasStoredNearbyClaim = false)

        val spatialMatch = activeHotspots.values
            .asSequence()
            .filterNot { hotspot -> hotspot.isAnnounced }
            .filter { hotspot -> hotspot.share.location.distanceSq(share.location) <= nearbyShareRadiusSq }
            .minByOrNull { hotspot -> hotspot.share.location.distanceSq(share.location) }
        if (spatialMatch != null) {
            spatialMatch.isAnnounced = true
            return FishingHotspotPartyShareResult(spatialMatch.id, hasStoredNearbyClaim = false)
        }

        nearbyPartyClaims.removeIf { claim -> claim.share.matches(share) }
        nearbyPartyClaims += NearbyFishingHotspotPartyClaim(share, now + nearbyClaimMillis)
        return FishingHotspotPartyShareResult(null, hasStoredNearbyClaim = true)
    }

    fun markAnnounced(id: FishingHotspotId) {
        activeHotspots[id]?.isAnnounced = true
    }

    fun activeHotspot(id: FishingHotspotId): TrackedFishingHotspot? =
        activeHotspots[id]?.snapshot()

    fun activeHotspots(): List<TrackedFishingHotspot> =
        activeHotspots.values.map(ActiveFishingHotspot::snapshot)

    fun nearestHotspotId(location: WorldVec): FishingHotspotId? =
        activeHotspots.values.minByOrNull { hotspot -> hotspot.share.location.distanceSq(location) }?.id

    fun reset() {
        activeHotspots.clear()
        nearbyPartyClaims.clear()
        nextHotspotId = 1L
    }

    private fun matchingHotspotId(
        observation: FishingHotspotObservation,
        candidateIds: Set<FishingHotspotId>,
    ): FishingHotspotId? {
        val exactId = candidateIds.firstOrNull { id -> activeHotspots[id]?.entityUuid == observation.entityUuid }
        if (exactId != null) return exactId
        return candidateIds
            .asSequence()
            .mapNotNull(activeHotspots::get)
            .filter { hotspot -> hotspot.share.matches(observation.share) }
            .minByOrNull { hotspot -> hotspot.share.location.distanceSq(observation.share.location) }
            ?.id
    }

    private fun applyNearbyClaims(appearedIds: List<FishingHotspotId>) {
        val availableIds = appearedIds.toMutableSet()
        val consumedClaims = mutableListOf<NearbyFishingHotspotPartyClaim>()
        nearbyPartyClaims.forEach { claim ->
            val match = availableIds
                .asSequence()
                .mapNotNull(activeHotspots::get)
                .filter { hotspot -> hotspot.share.location.distanceSq(claim.share.location) <= nearbyShareRadiusSq }
                .minByOrNull { hotspot -> hotspot.share.location.distanceSq(claim.share.location) }
                ?: return@forEach
            match.isAnnounced = true
            availableIds -= match.id
            consumedClaims += claim
        }
        nearbyPartyClaims.removeAll(consumedClaims.toSet())
    }

    private fun closeMissingHotspots(
        missingIds: Collection<FishingHotspotId>,
        isLocationLoaded: (WorldVec) -> Boolean,
        now: Long,
    ): List<TrackedFishingHotspot> {
        val closed = mutableListOf<TrackedFishingHotspot>()
        missingIds.forEach { id ->
            val hotspot = activeHotspots[id] ?: return@forEach
            hotspot.isVisible = false
            if (!isLocationLoaded(hotspot.share.location)) {
                hotspot.missingSinceMillis = null
                return@forEach
            }
            val missingSince = hotspot.missingSinceMillis ?: now.also { hotspot.missingSinceMillis = it }
            if (now - missingSince < missingGraceMillis) return@forEach
            closed += hotspot.snapshot()
            activeHotspots.remove(id)
        }
        return closed
    }

    private fun pruneClaims(now: Long) {
        nearbyPartyClaims.removeIf { claim -> claim.expiresAtMillis <= now }
    }

    private data class ActiveFishingHotspot(
        val id: FishingHotspotId,
        var entityUuid: UUID,
        var share: FishingHotspotShare,
        var lastSeenAtMillis: Long,
        var isVisible: Boolean = true,
        var isAnnounced: Boolean = false,
        var missingSinceMillis: Long? = null,
    ) {
        fun observe(observation: FishingHotspotObservation, now: Long) {
            entityUuid = observation.entityUuid
            share = observation.share
            lastSeenAtMillis = now
            isVisible = true
            missingSinceMillis = null
        }

        fun snapshot(): TrackedFishingHotspot = TrackedFishingHotspot(
            id = id,
            entityUuid = entityUuid,
            share = share,
            lastSeenAtMillis = lastSeenAtMillis,
            isVisible = isVisible,
            isAnnounced = isAnnounced,
        )
    }

    private companion object {
        const val MISSING_GRACE_MILLIS = 1_000L
        const val NEARBY_CLAIM_MILLIS = 5_000L
        const val NEARBY_SHARE_RADIUS_SQ = 20.0 * 20.0
    }
}
