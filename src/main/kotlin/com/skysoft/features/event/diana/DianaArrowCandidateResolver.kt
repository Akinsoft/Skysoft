package com.skysoft.features.event.diana

import com.skysoft.utils.WorldVec
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import kotlin.math.abs
import kotlin.math.floor

internal data class ResolvedArrowCandidate(
    val raw: WorldVec,
    val location: WorldVec,
    val distanceFromOrigin: Double,
    val distanceToRay: Double,
    val scaledDistanceToRay: Double,
    val order: Int,
    val loaded: Boolean,
    val surfaceResolved: Boolean,
    val surfaceSource: DianaArrowCandidateSurfaceSource = if (loaded) {
        DianaArrowCandidateSurfaceSource.LIVE
    } else {
        DianaArrowCandidateSurfaceSource.UNKNOWN
    },
)

internal enum class DianaArrowCandidateSurfaceSource {
    LIVE,
    CACHED,
    UNKNOWN,
}

internal object DianaArrowCandidateResolver {
    fun resolve(ray: DianaArrowRay, candidates: List<DianaArrowCandidate>): List<ResolvedArrowCandidate> {
        return rank(
            candidates.flatMapIndexed { index, candidate -> candidate.resolveLoadedSurfaceCandidates(ray, index) },
            ray.distanceHint,
        )
    }

    fun rank(
        candidates: List<ResolvedArrowCandidate>,
        distanceHint: DianaArrowDistance? = null,
    ): List<ResolvedArrowCandidate> {
        val bestByBlock = linkedMapOf<String, ResolvedArrowCandidate>()
        for (candidate in candidates) {
            val key = candidate.location.blockKey()
            val current = bestByBlock[key]
            if (current == null || candidate.isBetterThan(current)) {
                bestByBlock[key] = candidate
            }
        }
        val comparator = when {
            distanceHint == DianaArrowDistance.YELLOW && bestByBlock.size >= YELLOW_AMBIGUOUS_CANDIDATE_COUNT ->
                YELLOW_AMBIGUOUS_CANDIDATE_COMPARATOR
            distanceHint != null && bestByBlock.size >= AMBIGUOUS_CANDIDATE_COUNT ->
                ambiguousCandidateComparator(distanceHint)
            else -> resolvedCandidateComparator(distanceHint)
        }
        return bestByBlock.values.sortedWith(comparator)
    }

    private fun DianaArrowCandidate.resolveLoadedSurfaceCandidates(
        ray: DianaArrowRay,
        order: Int,
    ): List<ResolvedArrowCandidate> {
        val level = Minecraft.getInstance().level
            ?: return resolveCachedSurface(ray, order)
        val blockPos = BlockPos(block.x.toInt(), block.y.toInt(), block.z.toInt())
        if (!level.isLoaded(blockPos)) return resolveCachedSurface(ray, order)
        return VERTICAL_SURFACE_SCAN_OFFSETS
            .asSequence()
            .map { offset -> blockPos.verticalOffset(offset) }
            .filter { candidate -> DianaBurrowSurfaceValidator.isValid(level, candidate) }
            .mapNotNull { candidate -> DianaArrowProjector.scoreBlock(ray, candidate.toWorldVec()) }
            .map { candidate ->
                candidate.toResolved(
                    order = order,
                    raw = block,
                    loaded = true,
                    surfaceResolved = candidate.block != block,
                    surfaceSource = DianaArrowCandidateSurfaceSource.LIVE,
                )
            }
            .toList()
    }

    private fun DianaArrowCandidate.resolveCachedSurface(
        ray: DianaArrowRay,
        order: Int,
    ): List<ResolvedArrowCandidate> {
        val cached = DianaHubSurfaceCache.cachedSurface(block)
        return when (cached.status) {
            DianaCachedSurfaceStatus.VALID ->
                cached.location
                    ?.let { location -> DianaArrowProjector.scoreBlock(ray, location) }
                    ?.let { candidate ->
                        listOf(
                            candidate.toResolved(
                                order = order,
                                raw = block,
                                loaded = false,
                                surfaceResolved = candidate.block != block,
                                surfaceSource = DianaArrowCandidateSurfaceSource.CACHED,
                            ),
                        )
                    }
                    .orEmpty()
            DianaCachedSurfaceStatus.INVALID -> emptyList()
            DianaCachedSurfaceStatus.UNKNOWN -> listOf(
                toResolved(
                    order = order,
                    loaded = false,
                    surfaceResolved = false,
                    surfaceSource = DianaArrowCandidateSurfaceSource.UNKNOWN,
                ),
            )
        }
    }

    private fun DianaArrowCandidate.toResolved(
        order: Int,
        raw: WorldVec = block,
        loaded: Boolean,
        surfaceResolved: Boolean,
        surfaceSource: DianaArrowCandidateSurfaceSource = if (loaded) {
            DianaArrowCandidateSurfaceSource.LIVE
        } else {
            DianaArrowCandidateSurfaceSource.UNKNOWN
        },
    ): ResolvedArrowCandidate =
        ResolvedArrowCandidate(
            raw = raw,
            location = block,
            distanceFromOrigin = distanceFromOrigin,
            distanceToRay = distanceToRay,
            scaledDistanceToRay = scaledDistanceToRay,
            order = order,
            loaded = loaded,
            surfaceResolved = surfaceResolved,
            surfaceSource = surfaceSource,
        )

    private fun ResolvedArrowCandidate.isBetterThan(other: ResolvedArrowCandidate): Boolean =
        RESOLVED_CANDIDATE_COMPARATOR.compare(this, other) < 0

    private fun BlockPos.verticalOffset(offset: Int): BlockPos =
        when {
            offset > 0 -> above(offset)
            offset < 0 -> below(-offset)
            else -> this
        }

    private fun BlockPos.toWorldVec(): WorldVec = WorldVec(x.toDouble(), y.toDouble(), z.toDouble())

    private fun ambiguousCandidateComparator(distanceHint: DianaArrowDistance): Comparator<ResolvedArrowCandidate> =
        compareBy<ResolvedArrowCandidate> { candidate -> candidate.surfaceSource.knownSurfaceRank }
            .thenBy { candidate -> candidate.nominalDistanceRank(distanceHint) }
            .thenBy { candidate -> abs(candidate.distanceFromOrigin - distanceHint.midpoint) }
            .thenBy { candidate -> candidate.scaledDistanceToRay }
            .thenBy { candidate -> candidate.distanceFromOrigin }
            .thenBy { candidate -> abs(candidate.location.y - candidate.raw.y) }
            .thenBy { candidate -> candidate.surfaceSource.rank }
            .thenBy { candidate -> candidate.order }

    private val YELLOW_AMBIGUOUS_CANDIDATE_COMPARATOR =
        compareBy<ResolvedArrowCandidate> { candidate -> candidate.surfaceSource.knownSurfaceRank }
            .thenBy { candidate -> candidate.nominalDistanceRank(DianaArrowDistance.YELLOW) }
            .thenBy { candidate -> candidate.yellowExactRayRank() }
            .thenBy { candidate -> candidate.yellowExactRayAlignmentScore() }
            .thenBy { candidate -> candidate.yellowRayFitBucket() }
            .thenBy { candidate -> abs(candidate.distanceFromOrigin - DianaArrowDistance.YELLOW.midpoint) }
            .thenBy { candidate -> candidate.scaledDistanceToRay }
            .thenBy { candidate -> candidate.distanceFromOrigin }
            .thenBy { candidate -> abs(candidate.location.y - candidate.raw.y) }
            .thenBy { candidate -> candidate.surfaceSource.rank }
            .thenBy { candidate -> candidate.order }

    private fun ResolvedArrowCandidate.nominalDistanceRank(distanceHint: DianaArrowDistance?): Int =
        if (distanceHint == null || distanceFromOrigin.toInt() in distanceHint.minDistance..distanceHint.maxDistance) 0 else 1

    private fun ResolvedArrowCandidate.yellowRayFitBucket(): Int =
        floor(distanceToRay / YELLOW_RAY_FIT_BUCKET_BLOCKS).toInt()

    private fun ResolvedArrowCandidate.yellowExactRayRank(): Int =
        if (distanceToRay < YELLOW_EXACT_RAY_DISTANCE) 0 else 1

    private fun ResolvedArrowCandidate.yellowExactRayAlignmentScore(): Double =
        if (distanceToRay < YELLOW_EXACT_RAY_DISTANCE) scaledDistanceToRay else 0.0

    private const val VERTICAL_SURFACE_SCAN_RADIUS = 12
    private const val AMBIGUOUS_CANDIDATE_COUNT = 48
    private const val YELLOW_AMBIGUOUS_CANDIDATE_COUNT = 2
    private const val YELLOW_RAY_FIT_BUCKET_BLOCKS = 0.75
    private const val YELLOW_EXACT_RAY_DISTANCE = 0.1
    private val VERTICAL_SURFACE_SCAN_OFFSETS = (0..VERTICAL_SURFACE_SCAN_RADIUS).flatMap { offset ->
        if (offset == 0) listOf(0) else listOf(-offset, offset)
    }
    private fun resolvedCandidateComparator(
        distanceHint: DianaArrowDistance? = null,
    ): Comparator<ResolvedArrowCandidate> =
        compareBy<ResolvedArrowCandidate> { candidate -> candidate.surfaceSource.knownSurfaceRank }
            .thenBy { candidate -> candidate.nominalDistanceRank(distanceHint) }
            .thenBy { candidate -> candidate.scaledDistanceToRay }
            .thenBy { candidate -> candidate.distanceFromOrigin }
            .thenBy { candidate -> abs(candidate.location.y - candidate.raw.y) }
            .thenBy { candidate -> candidate.surfaceSource.rank }
            .thenBy { candidate -> candidate.order }

    private val RESOLVED_CANDIDATE_COMPARATOR = resolvedCandidateComparator()
}

private val DianaArrowCandidateSurfaceSource.knownSurfaceRank: Int
    get() = when (this) {
        DianaArrowCandidateSurfaceSource.LIVE,
        DianaArrowCandidateSurfaceSource.CACHED,
        -> 0
        DianaArrowCandidateSurfaceSource.UNKNOWN -> 1
    }

private val DianaArrowCandidateSurfaceSource.rank: Int
    get() = when (this) {
        DianaArrowCandidateSurfaceSource.LIVE -> LIVE_SURFACE_RANK
        DianaArrowCandidateSurfaceSource.CACHED -> CACHED_SURFACE_RANK
        DianaArrowCandidateSurfaceSource.UNKNOWN -> UNKNOWN_SURFACE_RANK
    }

private const val LIVE_SURFACE_RANK = 0
private const val CACHED_SURFACE_RANK = 1
private const val UNKNOWN_SURFACE_RANK = 2
